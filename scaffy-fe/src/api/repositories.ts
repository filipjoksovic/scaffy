import { apiFetch, throwApiError } from './client'

export type RepositoryConnection = {
  id: string
  provider: 'github'
  owner: string
  name: string
  url: string
  connectedAt: string
}

export type GitHubRepository = {
  fullName: string
  owner: string
  name: string
  url: string
  privateRepository: boolean
}

export async function listRepositoryConnections(): Promise<RepositoryConnection[]> {
  const response = await apiFetch('/api/repositories')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryConnection[]
}

export async function listGitHubRepositories(): Promise<GitHubRepository[]> {
  const response = await apiFetch('/api/repositories/github')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as GitHubRepository[]
}

export async function connectRepository(repository: string): Promise<RepositoryConnection> {
  const response = await apiFetch('/api/repositories', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ repository }),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryConnection
}

export async function disconnectRepository(id: string): Promise<void> {
  const response = await apiFetch(`/api/repositories/${id}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}
