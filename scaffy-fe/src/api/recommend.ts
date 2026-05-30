import { apiFetch, throwApiError } from './client'
import type { AnalysisResponse } from './analyze'

export type RecommendationPriority = 'high' | 'medium' | 'low' | (string & {})
export type RecommendationStatus = 'ok' | 'unavailable' | 'error' | (string & {})

export type Recommendation = {
  title: string
  description: string
  priority: RecommendationPriority
  reason: string
  nextStep: string
}

export type RecommendationResponse = {
  status: RecommendationStatus
  model: string | null
  recommendations: Recommendation[]
  message: string | null
}

export async function requestRecommendations(report: AnalysisResponse): Promise<RecommendationResponse> {
  const response = await apiFetch('/api/recommend', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(report),
  })

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json() as Promise<RecommendationResponse>
}

export type FindingFixRequest = {
  analysisRunId: string
  provider: string
  workflowPath: string
  workflowContent: string
  finding: {
    ruleId: string
    ruleLabel: string
    ruleDescription: string
    dimension: string
    capability: string
    type: string
    evidence: string | null
    location: string | null
    startLine: number | null
    endLine: number | null
  }
}

export type FindingFixEditMode = 'INSERT_AFTER' | 'REPLACE' | (string & {})

export type FindingFixEdit = {
  mode: FindingFixEditMode
  afterLine: number | null
  startLine: number | null
  endLine: number | null
  code: string | null
}

export type FindingFixResponse = {
  status: RecommendationStatus
  model: string | null
  summary: string | null
  explanation: string | null
  language: string | null
  suggestedCode: string | null
  edit: FindingFixEdit | null
  message: string | null
}

export async function requestFindingFix(request: FindingFixRequest): Promise<FindingFixResponse> {
  const response = await apiFetch('/api/recommend/finding', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json() as Promise<FindingFixResponse>
}
