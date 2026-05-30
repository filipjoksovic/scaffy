import { describe, expect, it } from 'vitest'
import type { Recommendation, RecommendationResponse } from '../../src/api/recommend'
import {
  errorMessage,
  loadingView,
  normalizePriority,
  priorityClassName,
  recommendationKey,
  viewFromError,
  viewFromResponse,
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
