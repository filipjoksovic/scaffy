import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addGitlabInstance,
  connectProviderUrl,
  currentUser,
  disconnectProvider,
  instanceLoginUrl,
  listConnections,
  listGitlabInstances,
  logout,
  oauthLoginUrl,
} from '../../src/api/auth'

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
      headers: expect.any(Headers),
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
      headers: expect.any(Headers),
    })
  })

  it('throws when logout fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 500, json: () => Promise.resolve({ error: 'boom' }) }),
    )
    await expect(logout()).rejects.toThrow('boom')
  })

  it('throws for non-401 current-user failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 503, json: () => Promise.resolve({ message: 'down' }) }),
    )
    await expect(currentUser()).rejects.toThrow('down')
  })
})

describe('auth URL builders', () => {
  it('exposes a gitlab login url', () => {
    expect(oauthLoginUrl.gitlab).toMatch(/\/oauth2\/authorization\/gitlab$/)
  })

  it('builds an instance login url', () => {
    expect(instanceLoginUrl('gitlab-company')).toMatch(
      /\/oauth2\/authorization\/gitlab-company$/,
    )
  })

  it('builds a connect url with no query when no options are given', () => {
    expect(connectProviderUrl('github')).toMatch(/\/api\/auth\/connect\/github$/)
  })

  it('builds a connect url with workspace and return query params', () => {
    const url = connectProviderUrl('gitlab', { workspaceId: 'ws-1', returnTo: '/dashboard' })
    expect(url).toContain('/api/auth/connect/gitlab?')
    expect(url).toContain('workspace=ws-1')
    expect(url).toContain('return=%2Fdashboard')
  })

  it('omits empty option values from the connect query', () => {
    const url = connectProviderUrl('github', { workspaceId: null, returnTo: '' })
    expect(url).toMatch(/\/api\/auth\/connect\/github$/)
  })
})

describe('provider connections API', () => {
  it('lists provider connections', async () => {
    const connections = [
      {
        provider: 'github',
        instance: '',
        displayName: 'octocat',
        scopes: ['repo'],
        connectedAt: '2026-05-26T12:00:00Z',
      },
    ]
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(connections) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listConnections()).resolves.toEqual(connections)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/auth\/connections$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('disconnects a provider without an instance', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await expect(disconnectProvider('github')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/auth\/connections\/github$/)
    expect(init.method).toBe('DELETE')
  })

  it('disconnects a provider with an encoded instance query', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await disconnectProvider('gitlab', 'gitlab.example.com')
    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('/api/auth/connections/gitlab?instance=gitlab.example.com')
  })

  it('throws when disconnect fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 404, json: () => Promise.resolve({ error: 'missing' }) }),
    )
    await expect(disconnectProvider('github')).rejects.toThrow('missing')
  })
})

describe('gitlab instances API', () => {
  it('lists gitlab instances', async () => {
    const instances = [{ registrationId: 'gitlab-company', host: 'gitlab.example.com', displayName: 'Company' }]
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(instances) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listGitlabInstances()).resolves.toEqual(instances)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/auth\/gitlab\/instances$/),
      { credentials: 'include', headers: expect.any(Headers) },
    )
  })

  it('adds a gitlab instance with a JSON body', async () => {
    const response = {
      instance: { registrationId: 'gitlab-company', host: 'gitlab.example.com', displayName: 'Company' },
      loginPath: '/oauth2/authorization/gitlab-company',
      callbackUrl: 'https://app/callback',
    }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(response) })
    vi.stubGlobal('fetch', fetchMock)

    const body = {
      baseUrl: 'https://gitlab.example.com',
      clientId: 'cid',
      clientSecret: 'secret',
      displayName: 'Company',
    }
    await expect(addGitlabInstance(body)).resolves.toEqual(response)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/auth\/gitlab\/instances$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('throws when adding a gitlab instance fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 422,
        json: () => Promise.resolve({ details: { baseUrl: 'invalid' } }),
      }),
    )
    await expect(
      addGitlabInstance({ baseUrl: 'x', clientId: 'c', clientSecret: 's' }),
    ).rejects.toThrow('baseUrl: invalid')
  })
})
