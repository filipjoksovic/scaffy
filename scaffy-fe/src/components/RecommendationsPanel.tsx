import { useEffect, useState } from 'react'
import {
  requestRecommendations,
  type Recommendation,
  type RecommendationResponse,
} from '../api/recommend'
import type { AnalysisResponse } from '../api/analyze'
import {
  errorMessage,
  loadingView,
  normalizePriority,
  priorityClassName,
  recommendationKey,
  viewFromError,
  viewFromResponse,
  type RecommendationsView,
} from '../lib/recommendations'
import { Badge } from './Badge'
import { Card } from './Card'
import { Eyebrow } from './Eyebrow'
import { StateRow } from './StateRow'

type RecommendationsState =
  | { kind: 'loading' }
  | { kind: 'success'; data: RecommendationResponse }
  | { kind: 'error'; message: string }

type Props = Readonly<{
  report: AnalysisResponse
}>

export function RecommendationsPanel({ report }: Props) {
  const [state, setState] = useState<RecommendationsState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    requestRecommendations(report)
      .then((data) => {
        if (!cancelled) setState({ kind: 'success', data })
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ kind: 'error', message: errorMessage(error) })
      })
    return () => {
      cancelled = true
    }
  }, [report])

  const view = toView(state)

  return (
    <Card as="section" className="recommendations-panel">
      <header className="recommendations-panel__head">
        <Eyebrow>AI recommendations</Eyebrow>
        <h2>Suggested next steps</h2>
        <p>
          Concrete improvements generated from the analysis findings. Use them as a starting point;
          review each suggestion before applying.
        </p>
      </header>
      <RecommendationsBody view={view} />
    </Card>
  )
}

function toView(state: RecommendationsState): RecommendationsView {
  if (state.kind === 'loading') return loadingView()
  if (state.kind === 'error') return viewFromError(state.message)
  return viewFromResponse(state.data)
}

function RecommendationsBody({ view }: Readonly<{ view: RecommendationsView }>) {
  if (view.kind === 'loading') {
    return <StateRow detail={view.detail} label={view.label} tone="loading" />
  }
  if (view.kind === 'error') {
    return <StateRow detail={view.detail} icon="!" label={view.label} tone="error" />
  }
  if (view.kind === 'unavailable' || view.kind === 'empty') {
    return <StateRow detail={view.detail} label={view.label} tone="empty" />
  }

  return (
    <div className="recommendations-list">
      {view.model && <p className="recommendations-list__meta">Model: {view.model}</p>}
      {view.recommendations.map((recommendation, index) => (
        <RecommendationCard
          key={recommendationKey(recommendation, index)}
          recommendation={recommendation}
        />
      ))}
    </div>
  )
}

function RecommendationCard({ recommendation }: Readonly<{ recommendation: Recommendation }>) {
  return (
    <article className="recommendation-card">
      <header className="recommendation-card__head">
        <h3>{recommendation.title}</h3>
        <PriorityBadge priority={recommendation.priority} />
      </header>
      <p className="recommendation-card__description">{recommendation.description}</p>
      <dl className="recommendation-card__meta">
        <div>
          <dt>Why</dt>
          <dd>{recommendation.reason}</dd>
        </div>
        <div>
          <dt>Next step</dt>
          <dd>{recommendation.nextStep}</dd>
        </div>
      </dl>
    </article>
  )
}

function PriorityBadge({ priority }: Readonly<{ priority: string }>) {
  return (
    <Badge className={priorityClassName(priority)}>{normalizePriority(priority)}</Badge>
  )
}
