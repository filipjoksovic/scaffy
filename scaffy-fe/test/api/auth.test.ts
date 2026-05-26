import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentUser, logout, oauthLoginUrl } from '../../src/api/auth'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('auth API', () => {
  it('points OAuth login links at backend authorization endpoints', () => {
    expect(oauthLoginUrl.google).toMatch(/\/oauth2\/authorization\/google$/)
    expect(oauthLoginUrl.github).toMatch(/\/oauth2\/authorization\/github$/)
  })

  it('loads the current user with credentials', async () => {
    const user = {
      id: '21a8c717-4598-426c-914d-a8053b2e8f5b',
      email: 'dev@example.com',
      displayName: 'Dev User',
      avatarUrl: 'https://example.com/avatar.png',
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(user),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(currentUser()).resolves.toEqual(user)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/auth\/me$/), {
      credentials: 'include',
    })
  })

  it('returns null for anonymous sessions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
      }),
    )

    await expect(currentUser()).resolves.toBeNull()
  })

  it('logs out with credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await expect(logout()).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/auth\/logout$/), {
      method: 'POST',
      credentials: 'include',
    })
  })
})
