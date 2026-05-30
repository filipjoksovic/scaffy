import type {
  Recommendation,
  RecommendationResponse,
  RecommendationStatus,
} from '../api/recommend'

export type RecommendationsViewKind =
  | 'loading'
  | 'list'
  | 'unavailable'
  | 'error'
  | 'empty'

export type RecommendationsView = Readonly<{
  kind: RecommendationsViewKind
  label: string
  detail: string
  recommendations: Recommendation[]
  model: string | null
}>

const DEFAULTS: Record<RecommendationsViewKind, Pick<RecommendationsView, 'label' | 'detail'>> = {
  loading: {
    label: 'Generating recommendations',
    detail: 'Calling /api/recommend with the current analysis report.',
  },
  unavailable: {
    label: 'Recommendations not configured',
    detail: 'The recommendation provider is not configured on this deployment.',
  },
  error: {
    label: 'Recommendations unavailable',
    detail: 'The recommendation provider returned an error.',
  },
  empty: {
    label: 'No recommendations',
    detail: 'The model returned no suggestions for this pipeline.',
  },
  list: {
    label: 'Recommendations',
    detail: '',
  },
}

export function viewFromResponse(response: RecommendationResponse): RecommendationsView {
  const status = response.status as RecommendationStatus
  if (status === 'unavailable') {
    return buildView('unavailable', response, response.message)
  }
  if (status === 'error') {
    return buildView('error', response, response.message)
  }
  if (response.recommendations.length === 0) {
    return buildView('empty', response, null)
  }
  return buildView('list', response, null)
}

export function viewFromError(message: string): RecommendationsView {
  return {
    kind: 'error',
    label: 'Recommendations failed',
    detail: message,
    recommendations: [],
    model: null,
  }
}

export function loadingView(): RecommendationsView {
  return {
    kind: 'loading',
    label: DEFAULTS.loading.label,
    detail: DEFAULTS.loading.detail,
    recommendations: [],
    model: null,
  }
}

export function priorityClassName(priority: string): string {
  const normalized = normalizePriority(priority)
  return `badge badge--priority badge--priority-${normalized}`
}

export function normalizePriority(priority: string): string {
  return (priority ?? '').toString().trim().toLowerCase() || 'medium'
}

export function recommendationKey(recommendation: Recommendation, index: number): string {
  return `${recommendation.title || 'recommendation'}-${index}`
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Failed to load recommendations.'
}

function buildView(
  kind: RecommendationsViewKind,
  response: RecommendationResponse,
  override: string | null,
): RecommendationsView {
  const defaults = DEFAULTS[kind]
  return {
    kind,
    label: defaults.label,
    detail: override ?? defaults.detail,
    recommendations: response.recommendations,
    model: response.model,
  }
}
