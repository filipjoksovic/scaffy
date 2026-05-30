export type ApiErrorResponse = {
  error?: string
  message?: string
  details?: Record<string, string>
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

let activeWorkspaceId: string | null = null

export function setActiveWorkspaceId(workspaceId: string | null): void {
  activeWorkspaceId = workspaceId
}

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

function rawFetch(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  if (activeWorkspaceId) {
    headers.set('X-Workspace-Id', activeWorkspaceId)
  }
  return fetch(apiUrl(path), {
    ...init,
    headers,
    credentials: 'include',
  })
}

// Single-flight refresh: many requests may 401 at once when the access token expires; they all
// await one /api/auth/refresh call rather than stampeding it.
let refreshInFlight: Promise<boolean> | null = null

function attemptRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(apiUrl('/api/auth/refresh'), {
      method: 'POST',
      credentials: 'include',
    })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const response = await rawFetch(path, init)
  if (response.status !== 401) {
    return response
  }
  // Access token likely expired — try a silent refresh, then replay the request once.
  const refreshed = await attemptRefresh()
  if (!refreshed) {
    return response
  }
  return rawFetch(path, init)
}

export async function throwApiError(response: Response): Promise<never> {
  let body: ApiErrorResponse | undefined
  try {
    body = (await response.json()) as ApiErrorResponse
  } catch {
    // fall through to status-based message
  }

  if (body?.details) {
    const fieldDetail = Object.entries(body.details)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join('; ')
    throw new Error(fieldDetail || body.message || body.error || `Request failed (${response.status})`)
  }

  throw new Error(body?.message || body?.error || `Request failed (${response.status})`)
}
