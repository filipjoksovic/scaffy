import { apiFetch, throwApiError } from './client'
import type { AnalysisResponse } from './analyze'

export type RepositoryProvider = 'github' | 'gitlab'

export type MetricsStatus =
  | 'AVAILABLE'
  | 'TOKEN_MISSING'
  | 'TOKEN_EXPIRED'
  | 'SCOPE_INSUFFICIENT'
  | 'RATE_LIMITED'
  | 'WORKFLOW_NOT_FOUND'
  | 'NO_RUNS_IN_WINDOW'
  | 'PROVIDER_ERROR'
  | 'UNSUPPORTED'

export type RecentRunSummary = {
  id: number
  displayName: string
  branch: string | null
  conclusion: string | null
  durationSec: number
  startedAt: string
}

export type BranchHealth = {
  totalRuns: number
  failureCount: number
  failureRate: number
}

export type WorkflowMetrics = {
  totalRuns: number
  successCount: number
  failureCount: number
  successRate: number
  failureRate: number
  recentFailures7d: number
  medianDurationSec: number
  p95DurationSec: number
  deployStability: number | null
  durationTrend: 'improving' | 'stable' | 'degrading' | 'insufficient_data'
  lastRunAt: string | null
  lastSuccessAt: string | null
  windowDays: number
  source: string
  recentRuns: RecentRunSummary[]
  triggerDistribution: Record<string, number>
  branchBreakdown: Record<string, BranchHealth>
}

export type WorkflowMetricsResult = {
  status: MetricsStatus
  metrics?: WorkflowMetrics | null
  message?: string | null
}

export type RepositoryConnection = {
  id: string
  provider: RepositoryProvider
  instance: string
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
  status: 'succeeded' | 'failed'
  errorMessage: string | null
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
  workflowMetrics?: WorkflowMetricsResult | null
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

export type RepositoryPublicationStatus = 'queued' | 'running' | 'succeeded' | 'failed'

export type RepositoryPublicationLogLine = {
  id: number
  stream: 'system' | 'stdout' | 'stderr'
  message: string
  createdAt: string
}

export type RepositoryPublication = {
  publicationJobId: string
  status: RepositoryPublicationStatus
  progress?: string | null
  errorMessage?: string | null
  provider: 'github'
  repositoryName: string
  visibility: 'private'
  repositoryOwner?: string | null
  repositoryUrl?: string | null
  repositoryConnection?: RepositoryConnection | null
  logs: RepositoryPublicationLogLine[]
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
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

export async function listGitLabRepositories(instance = ''): Promise<GitHubRepository[]> {
  const query = instance ? `?instance=${encodeURIComponent(instance)}` : ''
  const response = await apiFetch(`/api/repositories/gitlab${query}`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as GitHubRepository[]
}

export type ConnectRepositoryInput = {
  repository: string
  provider?: RepositoryProvider
  instance?: string
}

export async function connectRepository(
  input: string | ConnectRepositoryInput,
): Promise<RepositoryConnection> {
  const body = typeof input === 'string' ? { repository: input } : input
  const response = await apiFetch('/api/repositories', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryConnection
}

export async function createRepositoryPublication(request: {
  initJobId: string
  repositoryName: string
  description?: string
}): Promise<RepositoryPublication> {
  const response = await apiFetch('/api/repositories/github/publications', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryPublication
}

export async function getRepositoryPublication(id: string): Promise<RepositoryPublication> {
  const response = await apiFetch(`/api/repositories/github/publications/${id}`)
  if (!response.ok) {
    await throwApiError(response)
  }
  return (await response.json()) as RepositoryPublication
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
