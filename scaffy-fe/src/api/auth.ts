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
