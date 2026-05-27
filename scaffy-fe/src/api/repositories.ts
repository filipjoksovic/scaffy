import { apiFetch, throwApiError } from './client'
import type { AnalysisResponse } from './analyze'

export type RepositoryConnection = {
  id: string
  provider: 'github'
  owner: string
  name: string
  url: string
  connectedAt: string
  analysisRunCount: number
  analysisSummary: RepositoryAnalysisSummary | null
}

export type RepositoryAnalysisSummary = {
  runId: string
  runNumber: number
  analyzedAt: string
  workflowPath: string
  workflowContentHash: string | null
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
  runId: string
  repositoryId: string
  repository: string
  runNumber: number
  workflowPath: string
  workflowContentHash: string | null
  workflowContent: string | null
  analyzedAt: string
  analysisSchemaVersion: number
  analyzerModelVersion: string
  analysis: AnalysisResponse
}

export type RepositoryAnalysisRunSummary = RepositoryAnalysisSummary

export type DeltaDirection = 'improved' | 'worsened' | 'unchanged' | 'mixed'
export type FindingChangeKind = 'added' | 'removed' | 'unchanged'

export type RepositoryAnalysisDelta = {
  hasPrevious: boolean
  baseRun: RepositoryAnalysisRunSummary | null
  currentRun: RepositoryAnalysisRunSummary
  overall: ScoreDelta | null
  dimensions: DimensionDelta[]
  capabilities: CapabilityDelta[]
  findingChanges: FindingChange[]
}

export type ScoreDelta = {
  baseScore: number
  currentScore: number
  scoreDelta: number
  baseLevel: number
  currentLevel: number
  levelDelta: number
  baseStatus: string
  currentStatus: string
  direction: DeltaDirection
}

export type DimensionDelta = ScoreDelta & {
  dimension: string
}

export type CapabilityDelta = {
  dimension: string
  capability: string
  basePoints: number
  currentPoints: number
  pointsDelta: number
  baseFindingCount: number
  currentFindingCount: number
  findingCountDelta: number
  direction: DeltaDirection
}

export type FindingChange = {
  ruleId: string
  dimension: string
  capability: string
  type: string
  evidence: string | null
  location: string | null
  kind: FindingChangeKind
  direction: DeltaDirection
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

export async function listRepositoryAnalysisRuns(
  id: string,
): Promise<RepositoryAnalysisRunSummary[]> {
  const response = await apiFetch(`/api/repositories/${id}/analysis/runs`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryAnalysisRunSummary[]
}

export async function getRepositoryAnalysisDelta(
  id: string,
): Promise<RepositoryAnalysisDelta> {
  const response = await apiFetch(`/api/repositories/${id}/analysis/delta`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryAnalysisDelta
}
