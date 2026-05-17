export type PipelineProvider = 'github-actions' | 'gitlab-ci'

export type AnalysisStatus = 'missing' | 'partial' | 'complete'

export type Confidence = 'low' | 'medium' | 'high'

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
  confidence: Confidence
  detectedPractices: DetectedPractice[]
  missingPractices: string[]
}

export type AnalysisResponse = {
  provider: PipelineProvider
  dimensions: DimensionAnalysis[]
}

type AnalyzeErrorResponse = {
  error?: string
  message?: string
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

export async function analyzePipeline(file: File): Promise<AnalysisResponse> {
  const form = new FormData()
  form.append('file', file)

  const response = await fetch(apiUrl('/api/analyze'), {
    method: 'POST',
    body: form,
  })

  if (!response.ok) {
    let body: AnalyzeErrorResponse | undefined
    try {
      body = (await response.json()) as AnalyzeErrorResponse
    } catch {
      // fall through to status-based message
    }

    throw new Error(body?.message || body?.error || `Request failed (${response.status})`)
  }

  return response.json() as Promise<AnalysisResponse>
}
