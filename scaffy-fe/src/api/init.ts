import { apiFetch, throwApiError } from './client'

export type InitRequest = {
  projectName: string
  frontend: string
  backend: string
  pipeline: string
  includeDocker?: boolean
}

export type RuntimePreset = {
  id: string
  label: string
  runtime: string
  version: string
  lts: boolean
}

export type VersionPreset = {
  id: string
  label: string
  version: string
  defaultRuntimeId: string
  runtimes: RuntimePreset[]
}

export type StackCatalogOption = {
  id: string
  name: string
  description: string
  defaultVersionId: string
  versions: VersionPreset[]
}

export type PipelineCatalogOption = {
  id: string
  name: string
  description: string
}

export type InitCatalog = {
  frontends: StackCatalogOption[]
  backends: StackCatalogOption[]
  pipelines: PipelineCatalogOption[]
}

export type InitJobRequest = InitRequest & {
  frontendVersion: string
  frontendRuntime: string
  backendVersion: string
  backendRuntime: string
}

export type InitSelectedStack = {
  id: string
  name: string
  versionId: string
  versionLabel: string
  version: string
  runtimeId: string
  runtimeLabel: string
  runtime: string
  runtimeVersion: string
}

export type InitSelection = {
  frontend: InitSelectedStack
  backend: InitSelectedStack
  pipeline: {
    id: string
    name: string
  }
  includeDocker: boolean
}

export type InitJobStatus = 'queued' | 'running' | 'succeeded' | 'failed'

export type InitJob = {
  jobId: string
  status: InitJobStatus
  progress?: string | null
  errorMessage?: string | null
  selection: InitSelection
  downloadAvailable: boolean
  logs: InitJobLogLine[]
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
}

export type InitJobLogLine = {
  id: number
  stream: 'system' | 'stdout' | 'stderr'
  message: string
  createdAt: string
}

export async function getInitCatalog(): Promise<InitCatalog> {
  const response = await apiFetch('/api/init/catalog')

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json()
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

export async function createInitJob(request: InitJobRequest): Promise<InitJob> {
  const response = await apiFetch('/api/init/jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json()
}

export async function getInitJob(jobId: string): Promise<InitJob> {
  const response = await apiFetch(`/api/init/jobs/${jobId}`)

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json()
}

export async function downloadInitJob(jobId: string): Promise<Blob> {
  const response = await apiFetch(`/api/init/jobs/${jobId}/download`)

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
