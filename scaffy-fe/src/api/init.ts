export type InitRequest = {
  projectName: string
  frontend: string
  backend: string
  pipeline: string
  includeDocker?: boolean
}

type InitErrorResponse = {
  error?: string
  message?: string
  details?: Record<string, string>
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

export async function initProject(request: InitRequest): Promise<Blob> {
  const response = await fetch(apiUrl('/api/init'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let body: InitErrorResponse | undefined
    try {
      body = (await response.json()) as InitErrorResponse
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

  return response.blob()
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
