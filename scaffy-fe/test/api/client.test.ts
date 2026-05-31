import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, apiUrl, setActiveWorkspaceId, throwApiError } from '../../src/api/client'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  // Reset module-level workspace header between tests.
  setActiveWorkspaceId(null)
})

describe('apiUrl', () => {
  it('appends the path to the configured base url', () => {
    expect(apiUrl('/api/x')).toMatch(/\/api\/x$/)
    expect(apiUrl('/api/x').endsWith('/api/x')).toBe(true)
  })
})

describe('apiFetch headers', () => {
  it('omits X-Workspace-Id when no workspace is active', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/x')
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init.headers).get('X-Workspace-Id')).toBeNull()
    expect(init.credentials).toBe('include')
  })

  it('sets X-Workspace-Id when a workspace is active', async () => {
    setActiveWorkspaceId('ws-42')
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/x')
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init.headers).get('X-Workspace-Id')).toBe('ws-42')
  })
})

describe('apiFetch 401 refresh handling', () => {
  it('returns non-401 responses directly without refreshing', async () => {
    const ok = { status: 200, ok: true }
    const fetchMock = vi.fn().mockResolvedValue(ok)
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/api/x')).resolves.toBe(ok)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('refreshes then replays the request once on 401', async () => {
    const replayed = { status: 200, ok: true }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ status: 401 }) // initial request
      .mockResolvedValueOnce({ ok: true }) // refresh succeeds
      .mockResolvedValueOnce(replayed) // replayed request
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/api/x')).resolves.toBe(replayed)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const [refreshUrl, refreshInit] = fetchMock.mock.calls[1]
    expect(refreshUrl).toMatch(/\/api\/auth\/refresh$/)
    expect(refreshInit.method).toBe('POST')
  })

  it('returns the original 401 when refresh fails', async () => {
    const unauthorized = { status: 401 }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unauthorized) // initial request
      .mockResolvedValueOnce({ ok: false }) // refresh fails
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/api/x')).resolves.toBe(unauthorized)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('treats a thrown refresh as a failed refresh', async () => {
    const unauthorized = { status: 401 }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unauthorized)
      .mockRejectedValueOnce(new Error('network down'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/api/x')).resolves.toBe(unauthorized)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

describe('throwApiError', () => {
  function fakeResponse(status: number, body: unknown | (() => never)) {
    return {
      status,
      json: () => (typeof body === 'function' ? Promise.reject(new Error('no body')) : Promise.resolve(body)),
    } as unknown as Response
  }

  it('joins field details into a single message', async () => {
    await expect(
      throwApiError(fakeResponse(422, { details: { name: 'required', email: 'invalid' } })),
    ).rejects.toThrow('name: required; email: invalid')
  })

  it('prefers message over error', async () => {
    await expect(
      throwApiError(fakeResponse(400, { message: 'Bad input', error: 'BAD_REQUEST' })),
    ).rejects.toThrow('Bad input')
  })

  it('falls back to the error field', async () => {
    await expect(throwApiError(fakeResponse(409, { error: 'Conflict' }))).rejects.toThrow('Conflict')
  })

  it('falls back to a status message when the body cannot be parsed', async () => {
    await expect(throwApiError(fakeResponse(503, () => undefined as never))).rejects.toThrow(
      'Request failed (503)',
    )
  })

  it('uses message/error fallback when details object is empty', async () => {
    await expect(
      throwApiError(fakeResponse(422, { details: {}, message: 'Validation failed' })),
    ).rejects.toThrow('Validation failed')
  })
})
