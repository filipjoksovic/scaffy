import { useEffect, useState } from 'react'
import {
  requestRecommendations,
  type Recommendation,
  type RecommendationResponse,
} from '../api/recommend'
import type { AnalysisResponse } from '../api/analyze'
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
        if (cancelled) return
        const message = error instanceof Error ? error.message : 'Failed to load recommendations.'
        setState({ kind: 'error', message })
      })
    return () => {
      cancelled = true
    }
  }, [report])

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
      <RecommendationsBody state={state} />
    </Card>
  )
}

function RecommendationsBody({ state }: { state: RecommendationsState }) {
  if (state.kind === 'loading') {
    return (
      <StateRow
        detail="Calling /api/recommend with the current analysis report."
        label="Generating recommendations"
        tone="loading"
      />
    )
  }

  if (state.kind === 'error') {
    return (
      <StateRow
        detail={state.message}
        icon="!"
        label="Recommendations failed"
        tone="error"
      />
    )
  }

  const { data } = state

  if (data.status === 'unavailable') {
    return (
      <StateRow
        detail={data.message ?? 'The recommendation provider is not configured on this deployment.'}
        label="Recommendations not configured"
        tone="empty"
      />
    )
  }

  if (data.status === 'error') {
    return (
      <StateRow
        detail={data.message ?? 'The recommendation provider returned an error.'}
        icon="!"
        label="Recommendations unavailable"
        tone="error"
      />
    )
  }

  if (data.recommendations.length === 0) {
    return (
      <StateRow
        detail="The model returned no suggestions for this pipeline."
        label="No recommendations"
        tone="empty"
      />
    )
  }

  return (
    <div className="recommendations-list">
      {data.model && <p className="recommendations-list__meta">Model: {data.model}</p>}
      {data.recommendations.map((recommendation, index) => (
        <RecommendationCard key={`${recommendation.title}-${index}`} recommendation={recommendation} />
      ))}
    </div>
  )
}

function RecommendationCard({ recommendation }: { recommendation: Recommendation }) {
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

function PriorityBadge({ priority }: { priority: string }) {
  const normalized = priority.toLowerCase()
  const className = `badge badge--priority badge--priority-${normalized}`
  return <Badge className={className}>{normalized}</Badge>
}
