import { describe, expect, it } from 'vitest'
import type {
  FindingFixApplyResponse,
  Recommendation,
  RecommendationResponse,
} from '../../src/api/recommend'
import {
  applyFixView,
  buildApplyRequest,
  canSubmitCommit,
  DEFAULT_COMMIT_MESSAGE,
  errorMessage,
  loadingView,
  normalizePriority,
  priorityClassName,
  recommendationKey,
  viewFromError,
  viewFromResponse,
  type FixFinding,
} from '../../src/lib/recommendations'

function recommendation(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    title: 'Add timeout-minutes',
    description: 'Set timeout-minutes on every job.',
    priority: 'high',
    reason: 'MISSING_TIMEOUT smell detected.',
    nextStep: 'Add `timeout-minutes: 15` under each job.',
    ...overrides,
  }
}

function response(overrides: Partial<RecommendationResponse> = {}): RecommendationResponse {
  return {
    status: 'ok',
    model: 'gpt-4o-mini',
    recommendations: [recommendation()],
    message: null,
    ...overrides,
  }
}

describe('loadingView', () => {
  it('returns a loading view with default label and detail', () => {
    const view = loadingView()
    expect(view.kind).toBe('loading')
    expect(view.label).toMatch(/generat/i)
    expect(view.detail).toContain('/api/recommend')
    expect(view.recommendations).toEqual([])
    expect(view.model).toBeNull()
  })
})

describe('viewFromError', () => {
  it('wraps an arbitrary message into an error view', () => {
    const view = viewFromError('boom')
    expect(view.kind).toBe('error')
    expect(view.label).toBe('Recommendations failed')
    expect(view.detail).toBe('boom')
    expect(view.recommendations).toEqual([])
  })
})

describe('viewFromResponse', () => {
  it('returns a list view for a successful response with recommendations', () => {
    const view = viewFromResponse(response())
    expect(view.kind).toBe('list')
    expect(view.recommendations).toHaveLength(1)
    expect(view.model).toBe('gpt-4o-mini')
  })

  it('returns an empty view when the model returned no suggestions', () => {
    const view = viewFromResponse(response({ recommendations: [] }))
    expect(view.kind).toBe('empty')
    expect(view.label).toBe('No recommendations')
    expect(view.recommendations).toEqual([])
  })

  it('returns an unavailable view when the provider is not configured', () => {
    const view = viewFromResponse(
      response({ status: 'unavailable', recommendations: [], message: 'no api key' }),
    )
    expect(view.kind).toBe('unavailable')
    expect(view.detail).toBe('no api key')
  })

  it('falls back to a default detail when message is missing on unavailable', () => {
    const view = viewFromResponse(
      response({ status: 'unavailable', recommendations: [], message: null }),
    )
    expect(view.kind).toBe('unavailable')
    expect(view.detail).toContain('not configured')
  })

  it('returns an error view when the response status is error', () => {
    const view = viewFromResponse(
      response({ status: 'error', recommendations: [], message: 'upstream failed' }),
    )
    expect(view.kind).toBe('error')
    expect(view.detail).toBe('upstream failed')
  })
})

describe('normalizePriority', () => {
  it('lowercases and trims input', () => {
    expect(normalizePriority('HIGH')).toBe('high')
    expect(normalizePriority('  Medium  ')).toBe('medium')
    expect(normalizePriority('Low')).toBe('low')
  })

  it('defaults to medium for blank values', () => {
    expect(normalizePriority('')).toBe('medium')
    expect(normalizePriority('   ')).toBe('medium')
  })
})

describe('priorityClassName', () => {
  it('builds the priority badge class', () => {
    expect(priorityClassName('high')).toBe('badge badge--priority badge--priority-high')
    expect(priorityClassName('LOW')).toBe('badge badge--priority badge--priority-low')
    expect(priorityClassName('')).toBe('badge badge--priority badge--priority-medium')
  })
})

describe('recommendationKey', () => {
  it('combines title and index', () => {
    expect(recommendationKey(recommendation({ title: 'Pin actions' }), 2)).toBe('Pin actions-2')
  })

  it('falls back when title is empty', () => {
    expect(recommendationKey(recommendation({ title: '' }), 0)).toBe('recommendation-0')
  })
})

describe('errorMessage', () => {
  it('returns the message of an Error instance', () => {
    expect(errorMessage(new Error('http 500'))).toBe('http 500')
  })

  it('returns a generic message for non-Error values', () => {
    expect(errorMessage('something')).toContain('Failed')
    expect(errorMessage(null)).toContain('Failed')
    expect(errorMessage(undefined)).toContain('Failed')
  })
})

function sampleFinding(overrides: Partial<FixFinding> = {}): FixFinding {
  return {
    ruleId: 'MISSING_TIMEOUT',
    ruleLabel: 'Missing timeout',
    ruleDescription: 'Each job should set timeout-minutes.',
    dimension: 'workflow_quality',
    capability: 'Execution safety',
    type: 'SMELL',
    evidence: 'timeout-minutes not set',
    location: 'jobs.build',
    startLine: 10,
    endLine: 12,
    ...overrides,
  }
}

describe('buildApplyRequest', () => {
  it('builds the wire payload for the apply endpoint', () => {
    const request = buildApplyRequest({
      runId: 'run-1',
      workflowPath: '.github/workflows/ci.yml',
      modifiedContent: 'name: ci\n',
      commitMessage: '  Improve CI/CD pipeline quality  ',
      finding: sampleFinding(),
    })

    expect(request.analysisRunId).toBe('run-1')
    expect(request.workflowPath).toBe('.github/workflows/ci.yml')
    expect(request.workflowContent).toBe('name: ci\n')
    expect(request.commitMessage).toBe('Improve CI/CD pipeline quality')
    expect(request.finding.ruleId).toBe('MISSING_TIMEOUT')
    expect(request.finding.startLine).toBe(10)
  })

  it('sets commitMessage to null when the user clears the field', () => {
    const request = buildApplyRequest({
      runId: 'run-1',
      workflowPath: '.github/workflows/ci.yml',
      modifiedContent: 'name: ci\n',
      commitMessage: '   ',
      finding: sampleFinding(),
    })
    expect(request.commitMessage).toBeNull()
  })
})

describe('DEFAULT_COMMIT_MESSAGE', () => {
  it('is the spec-mandated default', () => {
    expect(DEFAULT_COMMIT_MESSAGE).toBe('Improve CI/CD pipeline quality')
  })
})

describe('applyFixView', () => {
  function success(overrides: Partial<FindingFixApplyResponse> = {}): FindingFixApplyResponse {
    return {
      status: 'ok',
      commitSha: 'abc123',
      commitUrl: 'https://example.test/commit/abc123',
      branch: 'main',
      message: null,
      ...overrides,
    }
  }

  it('returns the form view for idle state', () => {
    const view = applyFixView({ kind: 'idle' })
    expect(view.kind).toBe('form')
    expect(view.branch).toBeNull()
    expect(view.commitUrl).toBeNull()
    expect(view.message).toBeNull()
  })

  it('returns the submitting view while in flight', () => {
    expect(applyFixView({ kind: 'submitting' }).kind).toBe('submitting')
  })

  it('returns the error view when the request fails locally', () => {
    const view = applyFixView({ kind: 'error', message: 'network down' })
    expect(view.kind).toBe('error')
    expect(view.message).toBe('network down')
  })

  it('returns the success view with branch and commit url when status is ok', () => {
    const view = applyFixView({ kind: 'success', result: success() })
    expect(view.kind).toBe('success')
    expect(view.branch).toBe('main')
    expect(view.commitUrl).toBe('https://example.test/commit/abc123')
  })

  it('falls back to "default branch" when branch is missing on success', () => {
    const view = applyFixView({ kind: 'success', result: success({ branch: null }) })
    expect(view.branch).toBe('default branch')
  })

  it('returns the soft-error view when the response is unavailable', () => {
    const view = applyFixView({
      kind: 'success',
      result: success({ status: 'unavailable', message: 'no integration' }),
    })
    expect(view.kind).toBe('soft-error')
    expect(view.message).toBe('no integration')
  })

  it('returns a default message when soft-error has none', () => {
    const view = applyFixView({
      kind: 'success',
      result: success({ status: 'error', message: null }),
    })
    expect(view.kind).toBe('soft-error')
    expect(view.message).toContain('Reconnect')
  })
})

describe('canSubmitCommit', () => {
  it('allows submission when the message is non-empty and not submitting', () => {
    expect(canSubmitCommit({ kind: 'idle' }, 'Fix workflow')).toBe(true)
  })

  it('blocks submission when already submitting', () => {
    expect(canSubmitCommit({ kind: 'submitting' }, 'Fix workflow')).toBe(false)
  })

  it('blocks submission when the message is blank', () => {
    expect(canSubmitCommit({ kind: 'idle' }, '   ')).toBe(false)
    expect(canSubmitCommit({ kind: 'idle' }, '')).toBe(false)
  })
})
