import { apiUrl, throwApiError } from './client'

export type AnalysisStatus = 'missing' | 'partial' | 'complete' | 'not_evaluated' | (string & {})
export type AnalysisConfidence = 'low' | 'medium' | 'high' | (string & {})
export type PipelineProvider = 'github-actions' | 'gitlab-ci' | (string & {})

export type DetectedPractice = {
  practice: string
  evidence: string
  location: string
  metadata?: Record<string, string>
}

export type DimensionAnalysis = {
  dimension: string
  score: number
  level: number
  status: AnalysisStatus
  confidence: AnalysisConfidence
  detectedPractices: DetectedPractice[]
  missingPractices: string[]
}

export type AnalysisResponse = {
  provider: PipelineProvider
  overallScore: number
  overallLevel: number
  overallStatus: AnalysisStatus
  overallConfidence: AnalysisConfidence
  dimensions: DimensionAnalysis[]
}

export async function analyzePipeline(file: File): Promise<AnalysisResponse> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(apiUrl('/api/analyze'), {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    await throwApiError(response)
  }

  return response.json() as Promise<AnalysisResponse>
}
