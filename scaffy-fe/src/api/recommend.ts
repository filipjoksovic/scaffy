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
