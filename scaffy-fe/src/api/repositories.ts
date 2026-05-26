import { apiFetch, throwApiError } from './client'
import type { AnalysisResponse } from './analyze'

export type RepositoryConnection = {
  id: string
  provider: 'github'
  owner: string
  name: string
  url: string
  connectedAt: string
  analysisSummary: RepositoryAnalysisSummary | null
}

export type RepositoryAnalysisSummary = {
  analyzedAt: string
  workflowPath: string
  overallScore: number
  overallLevel: number
  overallStatus: string
  analysisSchemaVersion: number
  analyzerModelVersion: string
}

export type GitHubRepository = {
  fullName: string
  owner: string
  name: string
  url: string
  privateRepository: boolean
}

export type RepositoryAnalysis = {
  repositoryId: string
  repository: string
  workflowPath: string
  analyzedAt: string
  analysisSchemaVersion: number
  analyzerModelVersion: string
  analysis: AnalysisResponse
}

export async function listRepositoryConnections(): Promise<RepositoryConnection[]> {
  const response = await apiFetch('/api/repositories')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryConnection[]
}

export async function listGitHubRepositories(): Promise<GitHubRepository[]> {
  const response = await apiFetch('/api/repositories/github')
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as GitHubRepository[]
}

export async function connectRepository(repository: string): Promise<RepositoryConnection> {
  const response = await apiFetch('/api/repositories', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ repository }),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryConnection
}

export async function disconnectRepository(id: string): Promise<void> {
  const response = await apiFetch(`/api/repositories/${id}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
}

export async function analyzeRepository(id: string): Promise<RepositoryAnalysis> {
  const response = await apiFetch(`/api/repositories/${id}/analyze`, {
    method: 'POST',
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryAnalysis
}

export async function getRepositoryAnalysis(id: string): Promise<RepositoryAnalysis> {
  const response = await apiFetch(`/api/repositories/${id}/analysis`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryAnalysis
}
