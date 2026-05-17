export type ApiErrorResponse = {
  error?: string
  message?: string
  details?: Record<string, string>
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
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
