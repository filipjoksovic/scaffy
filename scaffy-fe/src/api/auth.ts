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
