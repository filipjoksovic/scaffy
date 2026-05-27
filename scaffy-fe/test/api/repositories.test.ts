import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  analyzeRepository,
  connectRepository,
  disconnectRepository,
  getRepositoryAnalysis,
  getRepositoryAnalysisDelta,
  listRepositoryAnalysisRuns,
  listGitHubRepositories,
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
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ repository: 'scaffy-labs/demo-app' }),
      credentials: 'include',
    })
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
    })
  })

  it('disconnects a repository with credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectRepository('repo-id')).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/repositories\/repo-id$/), {
      method: 'DELETE',
      credentials: 'include',
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
      },
    )
  })
})
