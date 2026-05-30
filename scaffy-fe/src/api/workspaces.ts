import { apiFetch, throwApiError } from './client'

export type WorkspaceRole = 'owner' | 'member'

export type Workspace = {
  id: string
  name: string
  slug: string
  role: string
  createdAt: string
}

export type WorkspaceMember = {
  userId: string
  email: string
  displayName: string | null
  avatarUrl: string | null
  role: string
  joinedAt: string
}

export type WorkspaceInvitation = {
  id: string
  workspaceId: string
  workspaceName: string
  email: string
  role: string
  token: string
  expiresAt: string
}

export type WorkspaceDetail = Workspace & {
  members: WorkspaceMember[]
  invitations: WorkspaceInvitation[]
}

export async function listWorkspaces(): Promise<Workspace[]> {
  const response = await apiFetch('/api/workspaces')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as Workspace[]
}

export async function createWorkspace(name: string): Promise<Workspace> {
  const response = await apiFetch('/api/workspaces', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as Workspace
}

export async function getWorkspace(workspaceId: string): Promise<WorkspaceDetail> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as WorkspaceDetail
}

export async function renameWorkspace(workspaceId: string, name: string): Promise<Workspace> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as Workspace
}

export async function inviteMember(
  workspaceId: string,
  email: string,
  role: WorkspaceRole = 'member',
): Promise<WorkspaceInvitation> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}/invitations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, role }),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as WorkspaceInvitation
}

export async function revokeInvitation(workspaceId: string, invitationId: string): Promise<void> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}/invitations/${invitationId}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}

export async function removeMember(workspaceId: string, userId: string): Promise<void> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}/members/${userId}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}

export async function listMyInvitations(): Promise<WorkspaceInvitation[]> {
  const response = await apiFetch('/api/workspaces/invitations')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as WorkspaceInvitation[]
}

export async function acceptInvitation(token: string): Promise<Workspace> {
  const response = await apiFetch(`/api/workspaces/invitations/${token}/accept`, {
    method: 'POST',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as Workspace
}

export type WorkspaceGitlabInstance = {
  id: string
  host: string
  baseUrl: string
  displayName: string | null
  registrationId: string
  connectPath: string
  connected: boolean
}

export type AddWorkspaceGitlabInstanceRequest = {
  baseUrl: string
  clientId: string
  clientSecret: string
  displayName?: string
}

export type AddWorkspaceGitlabInstanceResponse = {
  instance: WorkspaceGitlabInstance
  callbackUrl: string
}

export async function listWorkspaceGitlabInstances(
  workspaceId: string,
): Promise<WorkspaceGitlabInstance[]> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}/gitlab-instances`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as WorkspaceGitlabInstance[]
}

export async function addWorkspaceGitlabInstance(
  workspaceId: string,
  body: AddWorkspaceGitlabInstanceRequest,
): Promise<AddWorkspaceGitlabInstanceResponse> {
  const response = await apiFetch(`/api/workspaces/${workspaceId}/gitlab-instances`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as AddWorkspaceGitlabInstanceResponse
}

export async function deleteWorkspaceGitlabInstance(
  workspaceId: string,
  instanceId: string,
): Promise<void> {
  const response = await apiFetch(
    `/api/workspaces/${workspaceId}/gitlab-instances/${instanceId}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    await throwApiError(response)
  }
}
