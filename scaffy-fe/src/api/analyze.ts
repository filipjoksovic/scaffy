import { apiFetch, throwApiError } from './client'

export type AnalysisStatus = 'missing' | 'partial' | 'complete' | 'not_evaluated' | (string & {})
export type PipelineProvider = 'github-actions' | 'gitlab-ci' | (string & {})
export type FindingType = 'POSITIVE' | 'SMELL' | 'MISSING' | (string & {})

export type SourceSpan = {
  path: string
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

export type CapabilityFinding = {
  ruleId: string
  dimension: string
  capability: string
  type: FindingType
  evidence: string | null
  location: string | null
  source: SourceSpan | null
}

export type CapabilityScore = {
  capability: string
  points: number
  findings: CapabilityFinding[]
}

export type DimensionAnalysis = {
  dimension: string
  score: number
  level: number
  status: AnalysisStatus
  capabilityScores: CapabilityScore[]
}

export type AnalysisResponse = {
  provider: PipelineProvider
  overallScore: number
  overallLevel: number
  overallStatus: AnalysisStatus
  dimensions: DimensionAnalysis[]
}

export async function analyzePipeline(file: File): Promise<AnalysisResponse> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await apiFetch('/api/analyze', {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json() as Promise<AnalysisResponse>
}
