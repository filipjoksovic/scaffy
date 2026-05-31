import { apiFetch, apiUrl, throwApiError } from './client'

export type CurrentUser = {
  id: string
  email: string | null
  displayName: string | null
  avatarUrl: string | null
}

export const oauthLoginUrl = {
  google: apiUrl('/oauth2/authorization/google'),
  github: apiUrl('/oauth2/authorization/github'),
  gitlab: apiUrl('/oauth2/authorization/gitlab'),
}

export function instanceLoginUrl(registrationId: string): string {
  return apiUrl(`/oauth2/authorization/${registrationId}`)
}

/**
 * Account-linking ("connect") flow: links a provider to the current user in a specific workspace.
 * This is a full-page navigation, so the workspace + return path travel as query params (the
 * X-Workspace-Id header only rides on fetch requests).
 */
export function connectProviderUrl(
  registrationId: string,
  options: { workspaceId?: string | null; returnTo?: string } = {},
): string {
  const params = new URLSearchParams()
  if (options.workspaceId) {
    params.set('workspace', options.workspaceId)
  }
  if (options.returnTo) {
    params.set('return', options.returnTo)
  }
  const query = params.toString()
  const queryString = query ? `?${query}` : ''
  return apiUrl(`/api/auth/connect/${registrationId}${queryString}`)
}

export type ProviderConnection = {
  provider: 'github' | 'gitlab'
  instance: string
  displayName: string | null
  scopes: string[]
  connectedAt: string
}

export async function listConnections(): Promise<ProviderConnection[]> {
  const response = await apiFetch('/api/auth/connections')
  if (!response.ok) {
    await throwApiError(response)
  }
  return response.json() as Promise<ProviderConnection[]>
}

export async function disconnectProvider(provider: string, instance = ''): Promise<void> {
  const query = instance ? `?instance=${encodeURIComponent(instance)}` : ''
  const response = await apiFetch(`/api/auth/connections/${provider}${query}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}

export type GitlabInstance = {
  registrationId: string
  host: string
  displayName: string | null
}

export type AddGitlabInstanceRequest = {
  baseUrl: string
  clientId: string
  clientSecret: string
  displayName?: string
}

export type AddGitlabInstanceResponse = {
  instance: GitlabInstance
  loginPath: string
  callbackUrl: string
}

export async function listGitlabInstances(): Promise<GitlabInstance[]> {
  const response = await apiFetch('/api/auth/gitlab/instances')
  if (!response.ok) {
    await throwApiError(response)
  }
  return response.json() as Promise<GitlabInstance[]>
}

export async function addGitlabInstance(
  body: AddGitlabInstanceRequest,
): Promise<AddGitlabInstanceResponse> {
  const response = await apiFetch('/api/auth/gitlab/instances', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return response.json() as Promise<AddGitlabInstanceResponse>
}

export async function currentUser(): Promise<CurrentUser | null> {
  const response = await apiFetch('/api/auth/me')
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    await throwApiError(response)
  }
  return response.json() as Promise<CurrentUser>
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' })
  if (!response.ok) {
    await throwApiError(response)
  }
}
