import { afterEach, describe, expect, it, vi } from 'vitest'
import { analyzePipeline, type AnalysisResponse } from '../../src/api/analyze'

const report: AnalysisResponse = {
  provider: 'github-actions',
  overallScore: 0.74,
  overallLevel: 4,
  overallStatus: 'partial',
  dimensions: [
    {
      dimension: 'build_release',
      score: 0.85,
      level: 5,
      status: 'complete',
      capabilityScores: [
        {
          capability: 'Build scripting',
          points: 4,
          findings: [
            {
              ruleId: 'BUILD_STAGE_PRESENT',
              dimension: 'build_release',
              capability: 'Build scripting',
              type: 'POSITIVE',
              evidence: 'npm run build',
              location: 'jobs.build.steps[2].run',
            },
          ],
        },
      ],
    },
    {
      dimension: 'security_integration',
      score: 0.0,
      level: 0,
      status: 'not_evaluated',
      capabilityScores: [],
    },
  ],
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('analyzePipeline', () => {
  it('posts the selected file as multipart form data and returns the report', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(report),
    })
    vi.stubGlobal('fetch', fetchMock)

    const file = new File(['name: ci'], 'ci.yml', { type: 'text/yaml' })

    await expect(analyzePipeline(file)).resolves.toEqual(report)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/analyze$/)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.body).toBeInstanceOf(FormData)
    expect(init.body.get('file')).toBe(file)
    expect(init.headers).toBeUndefined()
  })

  it('surfaces validation errors from the API response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ message: 'Unsupported pipeline file.' }),
      }),
    )

    const file = new File(['not yaml'], 'ci.txt', { type: 'text/plain' })

    await expect(analyzePipeline(file)).rejects.toThrow('Unsupported pipeline file.')
  })

  it('falls back to the response status when the error body is malformed', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.reject(new Error('not json')),
      }),
    )

    const file = new File(['name: ci'], 'ci.yml', { type: 'text/yaml' })

    await expect(analyzePipeline(file)).rejects.toThrow('Request failed (500)')
  })
})
