import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  analyzeRepository,
  connectRepository,
  createRepositoryPublication,
  disconnectRepository,
  getRepositoryAnalysis,
  getRepositoryAnalysisDelta,
  getRepositoryPublication,
  listRepositoryAnalysisRuns,
  listGitHubRepositories,
  listGitLabRepositories,
  listRepositoryConnections,
} from '../../src/api/repositories'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('repository connections API', () => {
  it('lists repository connections with credentials', async () => {
    const connections = [
      {
        id: '1',
        provider: 'github',
        owner: 'scaffy-labs',
        name: 'demo-app',
        url: 'https://github.com/scaffy-labs/demo-app',
        connectedAt: '2026-05-26T12:00:00Z',
        analysisRunCount: 0,
        analysisSummary: null,
      },
    ]
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(connections),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listRepositoryConnections()).resolves.toEqual(connections)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('connects a repository with credentials', async () => {
    const connection = {
      id: '1',
      provider: 'github',
      owner: 'scaffy-labs',
      name: 'demo-app',
      url: 'https://github.com/scaffy-labs/demo-app',
      connectedAt: '2026-05-26T12:00:00Z',
      analysisRunCount: 0,
      analysisSummary: null,
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(connection),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(connectRepository('scaffy-labs/demo-app')).resolves.toEqual(connection)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories$/), {
      method: 'POST',
      headers: expect.any(Headers),
      body: JSON.stringify({ repository: 'scaffy-labs/demo-app' }),
      credentials: 'include',
    })
    const [, connectInit] = fetchMock.mock.calls[0]
    expect(new Headers(connectInit.headers).get('Content-Type')).toBe('application/json')
  })

  it('fetches GitHub repositories with credentials', async () => {
    const repositories = [
      {
        fullName: 'scaffy-labs/demo-app',
        owner: 'scaffy-labs',
        name: 'demo-app',
        url: 'https://github.com/scaffy-labs/demo-app',
        privateRepository: false,
      },
    ]
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(repositories),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listGitHubRepositories()).resolves.toEqual(repositories)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/github$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('disconnects a repository with credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectRepository('repo-id')).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/repo-id$/), {
      method: 'DELETE',
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('analyzes a repository with credentials', async () => {
    const analysis = {
      runId: 'run-1',
      repositoryId: 'repo-id',
      repository: 'scaffy-labs/demo-app',
      runNumber: 1,
      workflowPath: '.github/workflows/ci.yml',
      workflowContentHash: 'abc123',
      analyzedAt: '2026-05-26T12:00:00Z',
      analysisSchemaVersion: 1,
      analyzerModelVersion: 'capability-analyzer-v1',
      analysis: {
        provider: 'github-actions',
        overallScore: 0.4,
        overallLevel: 2,
        overallStatus: 'partial',
        dimensions: [],
      },
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(analysis),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(analyzeRepository('repo-id')).resolves.toEqual(analysis)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/repo-id\/analyze$/), {
      method: 'POST',
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('fetches persisted repository analysis with credentials', async () => {
    const analysis = {
      runId: 'run-1',
      repositoryId: 'repo-id',
      repository: 'scaffy-labs/demo-app',
      runNumber: 1,
      workflowPath: '.github/workflows/ci.yml',
      workflowContentHash: 'abc123',
      analyzedAt: '2026-05-26T12:00:00Z',
      analysisSchemaVersion: 1,
      analyzerModelVersion: 'capability-analyzer-v1',
      analysis: {
        provider: 'github-actions',
        overallScore: 0.4,
        overallLevel: 2,
        overallStatus: 'partial',
        dimensions: [],
      },
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(analysis),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(getRepositoryAnalysis('repo-id')).resolves.toEqual(analysis)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/repo-id\/analysis$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('fetches repository analysis runs with credentials', async () => {
    const runs = [
      {
        runId: 'run-2',
        runNumber: 2,
        analyzedAt: '2026-05-26T13:00:00Z',
        workflowPath: '.github/workflows/ci.yml',
        workflowContentHash: 'def456',
        overallScore: 0.6,
        overallLevel: 3,
        overallStatus: 'partial',
        analysisSchemaVersion: 1,
        analyzerModelVersion: 'capability-analyzer-v1',
      },
    ]
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(runs),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listRepositoryAnalysisRuns('repo-id')).resolves.toEqual(runs)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/repositories\/repo-id\/analysis\/runs$/),
      {
        credentials: 'include',
        headers: expect.any(Headers),
      },
    )
  })

  it('fetches repository analysis delta with credentials', async () => {
    const delta = {
      hasPrevious: true,
      baseRun: {
        runId: 'run-1',
        runNumber: 1,
        analyzedAt: '2026-05-26T12:00:00Z',
        workflowPath: '.github/workflows/ci.yml',
        workflowContentHash: 'abc123',
        overallScore: 0.4,
        overallLevel: 2,
        overallStatus: 'partial',
        analysisSchemaVersion: 1,
        analyzerModelVersion: 'capability-analyzer-v1',
      },
      currentRun: {
        runId: 'run-2',
        runNumber: 2,
        analyzedAt: '2026-05-26T13:00:00Z',
        workflowPath: '.github/workflows/ci.yml',
        workflowContentHash: 'def456',
        overallScore: 0.6,
        overallLevel: 3,
        overallStatus: 'partial',
        analysisSchemaVersion: 1,
        analyzerModelVersion: 'capability-analyzer-v1',
      },
      overall: {
        baseScore: 0.4,
        currentScore: 0.6,
        scoreDelta: 0.2,
        baseLevel: 2,
        currentLevel: 3,
        levelDelta: 1,
        baseStatus: 'partial',
        currentStatus: 'partial',
        direction: 'improved',
      },
      dimensions: [],
      capabilities: [],
      findingChanges: [],
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(delta),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(getRepositoryAnalysisDelta('repo-id')).resolves.toEqual(delta)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/repositories\/repo-id\/analysis\/delta$/),
      {
        credentials: 'include',
        headers: expect.any(Headers),
      },
    )
  })
})

describe('gitlab repositories + publications API', () => {
  const repositories = [
    {
      fullName: 'group/project',
      owner: 'group',
      name: 'project',
      url: 'https://gitlab.com/group/project',
      privateRepository: true,
    },
  ]

  it('lists GitLab repositories without an instance query', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(repositories) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listGitLabRepositories()).resolves.toEqual(repositories)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/gitlab$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('lists GitLab repositories with an encoded instance query', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(repositories) })
    vi.stubGlobal('fetch', fetchMock)

    await listGitLabRepositories('gitlab.example.com')
    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('/api/repositories/gitlab?instance=gitlab.example.com')
  })

  it('connects a repository from a structured input with provider + instance', async () => {
    const connection = {
      id: '2',
      provider: 'gitlab',
      owner: 'group',
      name: 'project',
      url: 'https://gitlab.example.com/group/project',
      connectedAt: '2026-05-26T12:00:00Z',
      analysisRunCount: 0,
      analysisSummary: null,
    }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(connection) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      connectRepository({ repository: 'group/project', provider: 'gitlab', instance: 'gitlab.example.com' }),
    ).resolves.toEqual(connection)
    const [, init] = fetchMock.mock.calls[0]
    expect(init.body).toBe(
      JSON.stringify({ repository: 'group/project', provider: 'gitlab', instance: 'gitlab.example.com' }),
    )
  })

  it('throws a parsed error when connect fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 409, json: () => Promise.resolve({ message: 'Already connected' }) }),
    )
    await expect(connectRepository('group/project')).rejects.toThrow('Already connected')
  })

  it('creates a GitHub repository publication with a JSON body', async () => {
    const publication = { id: 'pub-1', status: 'pending' }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(publication) })
    vi.stubGlobal('fetch', fetchMock)

    const request = { initJobId: 'job-1', repositoryName: 'demo-app', description: 'Generated' }
    await expect(createRepositoryPublication(request)).resolves.toEqual(publication)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/repositories\/github\/publications$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(request))
  })

  it('fetches a GitHub repository publication by id', async () => {
    const publication = { id: 'pub-1', status: 'completed' }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(publication) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(getRepositoryPublication('pub-1')).resolves.toEqual(publication)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/repositories\/github\/publications\/pub-1$/),
      { credentials: 'include', headers: expect.any(Headers) },
    )
  })
})
