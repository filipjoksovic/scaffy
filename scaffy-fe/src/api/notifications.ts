import { apiFetch, throwApiError } from './client'

export type AppNotification = {
  id: string
  workspaceId?: string | null
  type: string
  title: string
  message: string
  targetUrl?: string | null
  createdAt: string
}

export async function listNotifications(): Promise<AppNotification[]> {
  const response = await apiFetch('/api/notifications')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as AppNotification[]
}

export async function markNotificationRead(id: string): Promise<void> {
  const response = await apiFetch(`/api/notifications/${id}/read`, {
    method: 'POST',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}
