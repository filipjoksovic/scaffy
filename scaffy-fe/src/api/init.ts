import { apiFetch, throwApiError } from './client'

export type InitRequest = {
  projectName: string
  frontend: string
  backend: string
  pipeline: string
  includeDocker?: boolean
}

export async function initProject(request: InitRequest): Promise<Blob> {
  const response = await apiFetch('/api/init', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await throwApiError(response)
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
