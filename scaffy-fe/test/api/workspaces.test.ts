import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  acceptInvitation,
  addWorkspaceGitlabInstance,
  createWorkspace,
  deleteWorkspaceGitlabInstance,
  getWorkspace,
  inviteMember,
  listMyInvitations,
  listWorkspaceGitlabInstances,
  listWorkspaces,
  removeMember,
  renameWorkspace,
  revokeInvitation,
} from '../../src/api/workspaces'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function mockJson(value: unknown, ok = true) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok,
    status: ok ? 200 : 400,
    json: () => Promise.resolve(value),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const workspace = {
  id: 'ws-1',
  name: 'Acme',
  slug: 'acme',
  role: 'owner',
  createdAt: '2026-05-26T12:00:00Z',
}

const invitation = {
  id: 'inv-1',
  workspaceId: 'ws-1',
  workspaceName: 'Acme',
  email: 'dev@example.com',
  role: 'member',
  token: 'tok-123',
  expiresAt: '2026-06-01T12:00:00Z',
}

const gitlabInstance = {
  id: 'gl-1',
  host: 'gitlab.example.com',
  baseUrl: 'https://gitlab.example.com',
  displayName: 'Company GitLab',
  registrationId: 'gitlab-company',
  connectPath: '/oauth2/authorization/gitlab-company',
  connected: false,
}

describe('workspaces API', () => {
  it('lists workspaces', async () => {
    const fetchMock = mockJson([workspace])
    await expect(listWorkspaces()).resolves.toEqual([workspace])
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/workspaces$/), {
      credentials: 'include',
      headers: expect.any(Headers),
    })
  })

  it('creates a workspace with a JSON body', async () => {
    const fetchMock = mockJson(workspace)
    await expect(createWorkspace('Acme')).resolves.toEqual(workspace)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ name: 'Acme' }))
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('fetches a single workspace detail', async () => {
    const detail = { ...workspace, members: [], invitations: [] }
    const fetchMock = mockJson(detail)
    await expect(getWorkspace('ws-1')).resolves.toEqual(detail)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/workspaces\/ws-1$/),
      { credentials: 'include', headers: expect.any(Headers) },
    )
  })

  it('renames a workspace via PATCH', async () => {
    const fetchMock = mockJson(workspace)
    await expect(renameWorkspace('ws-1', 'Renamed')).resolves.toEqual(workspace)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1$/)
    expect(init.method).toBe('PATCH')
    expect(init.body).toBe(JSON.stringify({ name: 'Renamed' }))
  })

  it('invites a member with the default member role', async () => {
    const fetchMock = mockJson(invitation)
    await expect(inviteMember('ws-1', 'dev@example.com')).resolves.toEqual(invitation)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1\/invitations$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ email: 'dev@example.com', role: 'member' }))
  })

  it('invites a member with an explicit role', async () => {
    const fetchMock = mockJson(invitation)
    await inviteMember('ws-1', 'owner@example.com', 'owner')
    const [, init] = fetchMock.mock.calls[0]
    expect(init.body).toBe(JSON.stringify({ email: 'owner@example.com', role: 'owner' }))
  })

  it('revokes an invitation via DELETE and resolves to undefined', async () => {
    const fetchMock = mockJson(undefined)
    await expect(revokeInvitation('ws-1', 'inv-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1\/invitations\/inv-1$/)
    expect(init.method).toBe('DELETE')
  })

  it('removes a member via DELETE', async () => {
    const fetchMock = mockJson(undefined)
    await expect(removeMember('ws-1', 'user-9')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1\/members\/user-9$/)
    expect(init.method).toBe('DELETE')
  })

  it('lists the current user invitations', async () => {
    const fetchMock = mockJson([invitation])
    await expect(listMyInvitations()).resolves.toEqual([invitation])
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/workspaces\/invitations$/),
      { credentials: 'include', headers: expect.any(Headers) },
    )
  })

  it('accepts an invitation via POST', async () => {
    const fetchMock = mockJson(workspace)
    await expect(acceptInvitation('tok-123')).resolves.toEqual(workspace)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/invitations\/tok-123\/accept$/)
    expect(init.method).toBe('POST')
  })

  it('lists workspace gitlab instances', async () => {
    const fetchMock = mockJson([gitlabInstance])
    await expect(listWorkspaceGitlabInstances('ws-1')).resolves.toEqual([gitlabInstance])
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/workspaces\/ws-1\/gitlab-instances$/),
      { credentials: 'include', headers: expect.any(Headers) },
    )
  })

  it('adds a workspace gitlab instance with a JSON body', async () => {
    const response = { instance: gitlabInstance, callbackUrl: 'https://app/callback' }
    const fetchMock = mockJson(response)
    const body = {
      baseUrl: 'https://gitlab.example.com',
      clientId: 'cid',
      clientSecret: 'secret',
      displayName: 'Company GitLab',
    }
    await expect(addWorkspaceGitlabInstance('ws-1', body)).resolves.toEqual(response)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1\/gitlab-instances$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('deletes a workspace gitlab instance via DELETE', async () => {
    const fetchMock = mockJson(undefined)
    await expect(deleteWorkspaceGitlabInstance('ws-1', 'gl-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/workspaces\/ws-1\/gitlab-instances\/gl-1$/)
    expect(init.method).toBe('DELETE')
  })

  it('throws a parsed API error when the response is not ok', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ message: 'Forbidden' }),
      }),
    )
    await expect(listWorkspaces()).rejects.toThrow('Forbidden')
  })
})
