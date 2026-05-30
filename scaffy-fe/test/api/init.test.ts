import { afterEach, describe, expect, it, vi } from 'vitest'
import { initProject, type InitRequest } from '../../src/api/init'

const request: InitRequest = {
  projectName: 'demo-app',
  frontend: 'angular',
  backend: 'spring-boot',
  pipeline: 'github-actions',
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('initProject', () => {
  it('posts the init request and returns the ZIP blob', async () => {
    const blob = new Blob(['zip'])
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      blob: () => Promise.resolve(blob),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(initProject(request)).resolves.toBe(blob)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/init$/), {
      method: 'POST',
      credentials: 'include',
      headers: expect.any(Headers),
      body: JSON.stringify(request),
    })
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('surfaces validation details from the API response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ details: { projectName: 'must start with a letter' } }),
      }),
    )

    await expect(initProject(request)).rejects.toThrow('projectName: must start with a letter')
  })
})
