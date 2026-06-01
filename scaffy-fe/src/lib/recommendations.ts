import type {
  FindingFixApplyRequest,
  FindingFixApplyResponse,
  FindingFixRequest,
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

export const DEFAULT_COMMIT_MESSAGE = 'Improve CI/CD pipeline quality'

export type FixFinding = Readonly<{
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
}>

export type BuildApplyRequestInput = Readonly<{
  runId: string
  workflowPath: string
  modifiedContent: string
  commitMessage: string
  finding: FixFinding
}>

export function buildApplyRequest(input: BuildApplyRequestInput): FindingFixApplyRequest {
  return {
    analysisRunId: input.runId,
    workflowPath: input.workflowPath,
    workflowContent: input.modifiedContent,
    commitMessage: input.commitMessage.trim() || null,
    finding: input.finding satisfies FindingFixRequest['finding'],
  }
}

export type ApplyFixViewKind = 'form' | 'submitting' | 'success' | 'soft-error' | 'error'

export type ApplyFixView = Readonly<{
  kind: ApplyFixViewKind
  branch: string | null
  commitUrl: string | null
  message: string | null
}>

export function applyFixView(
  state:
    | { kind: 'idle' }
    | { kind: 'submitting' }
    | { kind: 'success'; result: FindingFixApplyResponse }
    | { kind: 'error'; message: string },
): ApplyFixView {
  if (state.kind === 'idle') {
    return { kind: 'form', branch: null, commitUrl: null, message: null }
  }
  if (state.kind === 'submitting') {
    return { kind: 'submitting', branch: null, commitUrl: null, message: null }
  }
  if (state.kind === 'error') {
    return { kind: 'error', branch: null, commitUrl: null, message: state.message }
  }
  const result = state.result
  if (result.status === 'ok') {
    return {
      kind: 'success',
      branch: result.branch ?? 'default branch',
      commitUrl: result.commitUrl,
      message: null,
    }
  }
  return {
    kind: 'soft-error',
    branch: null,
    commitUrl: null,
    message:
      result.message
      ?? 'The commit was rejected. Reconnect the repository and try again.',
  }
}

export function canSubmitCommit(
  state: { kind: 'idle' | 'submitting' | 'success' | 'error' },
  commitMessage: string,
): boolean {
  if (state.kind === 'submitting') return false
  return commitMessage.trim().length > 0
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
