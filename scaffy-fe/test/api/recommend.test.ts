import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  applyFindingFix,
  requestFindingFix,
  requestRecommendations,
  type FindingFixApplyRequest,
  type FindingFixApplyResponse,
  type FindingFixRequest,
  type FindingFixResponse,
  type RecommendationResponse,
} from '../../src/api/recommend'
import type { AnalysisResponse } from '../../src/api/analyze'

const sampleReport: AnalysisResponse = {
  provider: 'github-actions',
  overallScore: 0.3,
  overallLevel: 2,
  overallStatus: 'partial',
  dimensions: [
    {
      dimension: 'workflow_quality',
      score: 0.0,
      level: 1,
      status: 'missing',
      capabilityScores: [],
    },
  ],
}

const sampleResponse: RecommendationResponse = {
  status: 'ok',
  model: 'gpt-4o-mini',
  recommendations: [
    {
      title: 'Add timeout-minutes',
      description: 'Set timeout-minutes on every job.',
      priority: 'high',
      reason: 'MISSING_TIMEOUT smell detected on jobs.build.',
      nextStep: 'Add `timeout-minutes: 15` under each job.',
    },
  ],
  message: null,
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('requestRecommendations', () => {
  it('posts the analysis report as JSON and returns the recommendations response', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(sampleResponse),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestRecommendations(sampleReport)).resolves.toEqual(sampleResponse)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/recommend$/)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual(sampleReport)
  })

  it('returns an unavailable response when the provider is not configured', async () => {
    const unavailable: RecommendationResponse = {
      status: 'unavailable',
      model: null,
      recommendations: [],
      message: 'Recommendation provider is not configured',
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(unavailable),
      }),
    )

    await expect(requestRecommendations(sampleReport)).resolves.toEqual(unavailable)
  })

  it('throws when the API responds with a non-OK status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: 'Internal failure' }),
      }),
    )

    await expect(requestRecommendations(sampleReport)).rejects.toThrow('Internal failure')
  })
})

const sampleFixRequest: FindingFixRequest = {
  analysisRunId: '11111111-1111-1111-1111-111111111111',
  provider: 'github-actions',
  workflowPath: '.github/workflows/ci.yml',
  workflowContent: 'name: ci\non: [push]\n',
  finding: {
    ruleId: 'MISSING_TIMEOUT',
    ruleLabel: 'Job timeout missing',
    ruleDescription: 'Jobs should declare timeout-minutes.',
    dimension: 'workflow_quality',
    capability: 'Resilience',
    type: 'MISSING',
    evidence: null,
    location: null,
    startLine: null,
    endLine: null,
  },
}

const sampleFixResponse: FindingFixResponse = {
  status: 'ok',
  model: 'gpt-4o-mini',
  summary: 'Add timeout-minutes to the build job',
  explanation: 'Without a timeout a stuck job runs until the runner limit.',
  language: 'yaml',
  suggestedCode: 'jobs:\n  build:\n    timeout-minutes: 15\n',
  edit: null,
  message: null,
}

describe('requestFindingFix', () => {
  it('posts the finding fix request as JSON and returns the response', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(sampleFixResponse),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestFindingFix(sampleFixRequest)).resolves.toEqual(sampleFixResponse)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/recommend\/finding$/)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual(sampleFixRequest)
  })

  it('returns an unavailable response when the provider is not configured', async () => {
    const unavailable: FindingFixResponse = {
      status: 'unavailable',
      model: null,
      summary: null,
      explanation: null,
      language: null,
      suggestedCode: null,
      message: 'Recommendation provider is not configured',
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(unavailable),
      }),
    )

    await expect(requestFindingFix(sampleFixRequest)).resolves.toEqual(unavailable)
  })

  it('throws when the API responds with a non-OK status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: 'Internal failure' }),
      }),
    )

    await expect(requestFindingFix(sampleFixRequest)).rejects.toThrow('Internal failure')
  })
})

const sampleApplyRequest: FindingFixApplyRequest = {
  analysisRunId: 'run-1',
  workflowPath: '.github/workflows/ci.yml',
  workflowContent: 'name: ci\n',
  commitMessage: 'Improve CI/CD pipeline quality',
  finding: sampleFixRequest.finding,
}

const sampleApplyResponse: FindingFixApplyResponse = {
  status: 'ok',
  commitSha: 'abc123',
  commitUrl: 'https://github.com/o/r/commit/abc123',
  branch: 'main',
  message: null,
}

describe('applyFindingFix', () => {
  it('posts to /api/recommend/finding/apply with JSON body and returns commit info', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(sampleApplyResponse),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(applyFindingFix(sampleApplyRequest, 'workspace-1')).resolves.toEqual(sampleApplyResponse)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/recommend\/finding\/apply$/)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    const headers = new Headers(init.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-Workspace-Id')).toBe('workspace-1')
    expect(JSON.parse(init.body as string)).toEqual(sampleApplyRequest)
  })

  it('omits the workspace header when no workspace id is provided', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(sampleApplyResponse),
    })
    vi.stubGlobal('fetch', fetchMock)

    await applyFindingFix(sampleApplyRequest)

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-Workspace-Id')).toBeNull()
  })

  it('throws when the apply endpoint returns a non-OK status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ message: 'Unauthorized' }),
      }),
    )

    await expect(applyFindingFix(sampleApplyRequest, 'workspace-1'))
      .rejects.toThrow('Unauthorized')
  })

  it('returns the unavailable response body when the workspace has no integration', async () => {
    const unavailable: FindingFixApplyResponse = {
      status: 'unavailable',
      commitSha: null,
      commitUrl: null,
      branch: null,
      message: 'Connect a repository in this workspace before committing AI suggestions.',
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(unavailable),
      }),
    )

    await expect(applyFindingFix(sampleApplyRequest, 'workspace-1')).resolves.toEqual(unavailable)
  })
})
