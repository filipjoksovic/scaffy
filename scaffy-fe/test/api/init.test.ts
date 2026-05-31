import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  deleteFavouriteStack,
  getFavouriteStacks,
  initProject,
  saveFavouriteStack,
  type FavouriteStack,
  type InitRequest,
} from '../../src/api/init'

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
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
  })

  it('throws on error response', async () => {
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

// ---------------------------------------------------------------------------
// Favourite stacks
// ---------------------------------------------------------------------------

const mockFavourite: FavouriteStack = {
  id: 'b1a2c3d4-e5f6-7890-abcd-ef1234567890',
  userId: 'user-uuid',
  name: 'My Spring stack',
  frontend: 'react',
  frontendVersion: '19',
  frontendRuntime: 'node-22',
  backend: 'spring-boot',
  backendVersion: '4.0',
  backendRuntime: 'java-21',
  pipeline: 'github-actions',
  pipelineMaturity: 'l2',
  includeDocker: true,
  createdAt: '2026-01-01T12:00:00Z',
}

describe('getFavouriteStacks', () => {
  it('returns the list of favourites from the API', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve([mockFavourite]),
      }),
    )

    const result = await getFavouriteStacks()

    expect(result).toEqual([mockFavourite])
  })

  it('sends a GET request to /api/init/favourites with credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    })
    vi.stubGlobal('fetch', fetchMock)

    await getFavouriteStacks()

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/init\/favourites$/),
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('returns an empty array when the user has no favourites', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve([]),
      }),
    )

    await expect(getFavouriteStacks()).resolves.toEqual([])
  })

  it('throws on a non-ok response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ error: 'Unauthorized' }),
      }),
    )

    await expect(getFavouriteStacks()).rejects.toThrow()
  })
})

describe('saveFavouriteStack', () => {
  const saveRequest = {
    name: 'My Spring stack',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: true,
  }

  it('posts the request and returns the saved favourite', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockFavourite),
      }),
    )

    const result = await saveFavouriteStack(saveRequest)

    expect(result).toEqual(mockFavourite)
  })

  it('sends a POST to /api/init/favourites with JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockFavourite),
    })
    vi.stubGlobal('fetch', fetchMock)

    await saveFavouriteStack(saveRequest)

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/init\/favourites$/),
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(saveRequest),
      }),
    )
  })

  it('throws when the server rejects the request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 422,
        json: () => Promise.resolve({ error: 'Limit reached' }),
      }),
    )

    await expect(saveFavouriteStack(saveRequest)).rejects.toThrow()
  })
})

describe('deleteFavouriteStack', () => {
  it('sends a DELETE to /api/init/favourites/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await deleteFavouriteStack('some-uuid')

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/init\/favourites\/some-uuid$/),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('resolves without a value on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))

    await expect(deleteFavouriteStack('some-uuid')).resolves.toBeUndefined()
  })

  it('throws on a non-ok response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: 'Not found' }),
      }),
    )

    await expect(deleteFavouriteStack('missing-uuid')).rejects.toThrow()
  })
})
