import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { Link } from 'react-router-dom'
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer } from 'recharts'
import { Badge, Button, Card, Eyebrow, StateRow } from '../components'
import { analyzePipeline } from '../api/analyze'
import type { AnalysisResponse, DimensionAnalysis, PipelineProvider } from '../api/analyze'

type AnalyzeStatus =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; result: AnalysisResponse }
  | { kind: 'error'; message: string }

function formatProvider(provider: PipelineProvider): string {
  if (provider === 'github-actions') return 'GitHub Actions'
  if (provider === 'gitlab-ci') return 'GitLab CI'
  return provider
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export function Analyzer() {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState<AnalyzeStatus>({ kind: 'idle' })

  function onFileChange(e: ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0] ?? null
    setFile(selected)
    setStatus({ kind: 'idle' })
  }

  async function analyze() {
    if (!file) return
    setStatus({ kind: 'loading' })
    try {
      const result = await analyzePipeline(file)
      setStatus({ kind: 'success', result })
    } catch (err) {
      setStatus({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Analysis failed.',
      })
    }
  }

  return (
    <main className="app-shell">
      <nav aria-label="Primary" className="top-nav">
        <Link aria-label="Scaffy home" className="wordmark" to="/">
          <span aria-hidden="true" className="wordmark-mark" />
          Scaffy
        </Link>
        <div className="nav-links">
          <Link to="/">Initializer</Link>
          <Link to="/analyzer">Analyzer</Link>
          <Link to="/design">Design Language</Link>
        </div>
        <div className="nav-actions">
          <a
            className="text-link"
            href="https://github.com/filipjoksovic/scaffy"
            rel="noreferrer"
            target="_blank"
          >
            GitHub
          </a>
        </div>
      </nav>

      <section aria-labelledby="analyzer-title" className="analyzer-band">
        <header className="wizard-header">
          <Eyebrow>Pipeline analyzer</Eyebrow>
          <h1 id="analyzer-title">Measure CI/CD maturity across your pipeline.</h1>
          <p>
            Upload a GitHub Actions or GitLab CI YAML file. Scaffy scores it across build, test,
            and deployment dimensions and highlights what's missing.
          </p>
        </header>

        <div className="analyzer-upload">
          <label className="analyzer-upload__label" htmlFor="analyzer-file">
            <span className="analyzer-upload__label-text">
              {file ? file.name : 'Choose a .yml or .yaml file'}
            </span>
          </label>
          <input
            accept=".yml,.yaml"
            className="analyzer-upload__input"
            id="analyzer-file"
            onChange={onFileChange}
            type="file"
          />
          <Button disabled={!file || status.kind === 'loading'} onClick={analyze}>
            {status.kind === 'loading' ? 'Analyzing…' : 'Analyze'}
          </Button>
        </div>

        {(status.kind === 'loading' || status.kind === 'error') && (
          <div className="analyzer-feedback">
            {status.kind === 'loading' && (
              <StateRow
                detail="Reading pipeline structure and scoring dimensions…"
                label="Analyzing pipeline"
                tone="loading"
              />
            )}
            {status.kind === 'error' && (
              <StateRow
                detail={status.message}
                icon="!"
                label="Analysis failed"
                tone="error"
              />
            )}
          </div>
        )}

        {status.kind === 'success' && <AnalysisReport result={status.result} />}
      </section>

      <footer className="footer">
        <strong>Scaffy</strong>
        <Link to="/">Initializer</Link>
        <Link to="/analyzer">Analyzer</Link>
        <Link to="/design">Design</Link>
        <span>Iter 1 · Angular + Spring Boot</span>
      </footer>
    </main>
  )
}

type AnalysisReportProps = {
  result: AnalysisResponse
}

function AnalysisReport({ result }: AnalysisReportProps) {
  const { provider, dimensions, overallScore, overallLevel } = result
  const radarData = dimensions.map((d) => ({
    subject: capitalize(d.dimension),
    score: d.score,
  }))

  return (
    <div className="analyzer-result">
      <div className="analyzer-summary">
        <Card>
          <Eyebrow>Analysis summary</Eyebrow>
          <div className="analyzer-summary__score">
            <span className="analyzer-summary__score-value">{overallScore.toFixed(2)}</span>
            <span className="analyzer-summary__score-denom"> / 1.00</span>
          </div>
          <p className="analyzer-summary__level">Level {overallLevel} / 5</p>
          <dl className="analyzer-summary__meta">
            <div>
              <dt>Provider</dt>
              <dd>{formatProvider(provider)}</dd>
            </div>
            <div>
              <dt>Dimensions scored</dt>
              <dd>{dimensions.length}</dd>
            </div>
          </dl>
        </Card>

        <Card>
          <Eyebrow>Score breakdown</Eyebrow>
          <ResponsiveContainer height={220} width="100%">
            <RadarChart data={radarData}>
              <PolarGrid stroke="var(--color-hairline)" />
              <PolarAngleAxis
                dataKey="subject"
                tick={{ fill: 'var(--color-body)', fontSize: 13 }}
              />
              <Radar
                dataKey="score"
                fill="var(--color-primary)"
                fillOpacity={0.2}
                stroke="var(--color-primary)"
                strokeWidth={2}
              />
            </RadarChart>
          </ResponsiveContainer>
        </Card>
      </div>

      <div className="analyzer-dimension-grid">
        {dimensions.map((dim) => (
          <DimensionCard dim={dim} key={dim.dimension} />
        ))}
      </div>
    </div>
  )
}

type DimensionCardProps = {
  dim: DimensionAnalysis
}

function DimensionCard({ dim }: DimensionCardProps) {
  const hasDetected = dim.detectedPractices.length > 0
  const hasMissing = dim.missingPractices.length > 0
  const isEmpty = !hasDetected && !hasMissing

  return (
    <Card>
      <Eyebrow>{capitalize(dim.dimension)}</Eyebrow>
      <div className="analyzer-dim-score">
        <span className="analyzer-dim-score__value">{dim.score.toFixed(2)} / 1.00</span>
        <Badge className={`badge--${dim.status}`}>{dim.status}</Badge>
      </div>
      <p className="analyzer-dim-meta">
        Level {dim.level} / 5 · {dim.confidence} confidence
      </p>

      {isEmpty && (
        <p className="analyzer-no-practices">No practices analyzed for this dimension.</p>
      )}

      {hasDetected && (
        <>
          <p className="analyzer-section-label">Detected</p>
          <ul className="analyzer-practice-list">
            {dim.detectedPractices.map((practice, i) => (
              <li className="analyzer-practice-list__item" key={i}>
                <span>{practice.practice}</span>
                {practice.evidence && (
                  <code className="analyzer-practice-evidence">{practice.evidence}</code>
                )}
                {practice.location && (
                  <span className="analyzer-practice-location">{practice.location}</span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {hasMissing && (
        <>
          <p className="analyzer-section-label">Missing</p>
          <ul className="analyzer-practice-list analyzer-practice-list--missing">
            {dim.missingPractices.map((practice, i) => (
              <li className="analyzer-practice-list__item" key={i}>
                {practice}
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  )
}
