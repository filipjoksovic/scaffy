import type { ExtractedFile, PublisherConfig } from './types.js'

type GitHubUser = {
  login: string
}

type GitHubRepository = {
  owner: {
    login: string
  }
  default_branch?: string
  html_url: string
  name: string
}

type GitHubBlob = {
  sha: string
}

type GitHubCommit = {
  sha: string
  tree: {
    sha: string
  }
}

type GitHubRef = {
  object: {
    sha: string
  }
}

type GitHubContent = {
  sha: string
  type: string
}

export class GitHubPublisher {
  constructor(private readonly config: PublisherConfig) {}

  async publishRepository(
    token: string,
    repositoryName: string,
    description: string | null,
    files: ExtractedFile[],
  ): Promise<{ owner: string; name: string; url: string }> {
    const user = await this.request<GitHubUser>(token, '/user')
    const repository = await this.request<GitHubRepository>(token, '/user/repos', {
      method: 'POST',
      body: {
        name: repositoryName,
        description: description || undefined,
        private: true,
        auto_init: true,
      },
    })
    const owner = repository.owner.login || user.login
    const repo = repository.name
    const branch = await this.waitForDefaultBranch(token, owner, repo, repository.default_branch)
    await this.request<GitHubRef>(
      token,
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/git/ref/heads/${encodeURIComponent(branch)}`,
      {},
      `read ${owner}/${repo} ${branch} branch reference`,
    )

    for (const file of orderFilesForUpload(files)) {
      const existingSha = await this.existingFileSha(token, owner, repo, branch, file.path)
      await this.request<GitHubBlob>(
        token,
        `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents/${encodePath(file.path)}`,
        {
          method: 'PUT',
          body: {
            message: `Add ${file.path}`,
            content: file.content.toString('base64'),
            branch,
            ...(existingSha ? { sha: existingSha } : {}),
          },
        },
        `upload ${file.path}`,
      )
    }

    return {
      owner,
      name: repo,
      url: repository.html_url,
    }
  }

  private async request<T = unknown>(
    token: string,
    path: string,
    options: { method?: string; body?: unknown } = {},
    operation = 'call GitHub API',
  ): Promise<T> {
    const response = await fetch(`${this.config.githubApiUrl}${path}`, {
      method: options.method ?? 'GET',
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'X-GitHub-Api-Version': '2022-11-28',
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })

    if (!response.ok) {
      const message = await githubErrorMessage(response, operation)
      throw new Error(message)
    }

    if (response.status === 204) {
      return undefined as T
    }
    return (await response.json()) as T
  }

  private async waitForDefaultBranch(
    token: string,
    owner: string,
    repo: string,
    initialDefaultBranch: string | undefined,
  ): Promise<string> {
    let branch = initialDefaultBranch || 'main'
    let lastError: unknown

    for (let attempt = 1; attempt <= 8; attempt += 1) {
      try {
        const repository = await this.request<GitHubRepository>(
          token,
          `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`,
          {},
          `read ${owner}/${repo} repository metadata`,
        )
        branch = repository.default_branch || branch
        await this.request<GitHubRef>(
          token,
          `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/git/ref/heads/${encodeURIComponent(branch)}`,
          {},
          `wait for ${owner}/${repo} ${branch} branch initialization`,
        )
        return branch
      } catch (error) {
        lastError = error
        await delay(750 * attempt)
      }
    }

    throw lastError instanceof Error
      ? new Error(`GitHub repository was created, but its default branch was not ready. ${lastError.message}`)
      : new Error('GitHub repository was created, but its default branch was not ready.')
  }

  private async existingFileSha(
    token: string,
    owner: string,
    repo: string,
    branch: string,
    filePath: string,
  ): Promise<string | null> {
    const response = await fetch(
      `${this.config.githubApiUrl}/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents/${encodePath(filePath)}?ref=${encodeURIComponent(branch)}`,
      {
        headers: {
          Accept: 'application/vnd.github+json',
          Authorization: `Bearer ${token}`,
          'X-GitHub-Api-Version': '2022-11-28',
        },
      },
    )

    if (response.status === 404) {
      return null
    }
    if (!response.ok) {
      throw new Error(await githubErrorMessage(response, `check existing ${filePath}`))
    }

    const body = (await response.json()) as GitHubContent | GitHubContent[]
    if (Array.isArray(body)) {
      return null
    }
    return body.type === 'file' ? body.sha : null
  }
}

async function githubErrorMessage(response: Response, operation: string): Promise<string> {
  let message = `GitHub request failed (${response.status}).`
  try {
    const body = (await response.json()) as { message?: string; errors?: Array<{ message?: string }> }
    if (body.message) {
      message = body.message
    }
    const detail = body.errors?.map((error) => error.message).filter(Boolean).join('; ')
    if (detail) {
      message = `${message}: ${detail}`
    }
  } catch {
    // Keep the status-based message.
  }

  if (response.status === 401 || response.status === 403) {
    return `Reconnect with GitHub before creating repositories while trying to ${operation}. ${message}`
  }
  if (response.status === 404 && operation.includes('.github/workflows')) {
    return `Reconnect GitHub with workflow access before creating repository pipeline files. ${message}`
  }
  if (response.status === 422) {
    return `GitHub could not complete repository creation while trying to ${operation}. ${message}`
  }
  return `GitHub failed while trying to ${operation}. ${message}`
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function encodePath(filePath: string): string {
  return filePath.split('/').map(encodeURIComponent).join('/')
}

function orderFilesForUpload(files: ExtractedFile[]): ExtractedFile[] {
  return [...files].sort((left, right) => {
    const priorityDelta = uploadPriority(left.path) - uploadPriority(right.path)
    return priorityDelta || left.path.localeCompare(right.path)
  })
}

function uploadPriority(filePath: string): number {
  return filePath.startsWith('.github/workflows/') ? 1 : 0
}
