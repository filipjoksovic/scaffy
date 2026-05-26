import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  connectRepository,
  disconnectRepository,
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
})
