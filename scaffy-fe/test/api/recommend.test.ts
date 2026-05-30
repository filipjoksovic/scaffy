import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  requestRecommendations,
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
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
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
