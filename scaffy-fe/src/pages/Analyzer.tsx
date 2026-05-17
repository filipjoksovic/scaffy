import { type ChangeEvent, useMemo, useRef, useState } from 'react'
import { analyzePipeline, type AnalysisResponse, type DimensionAnalysis } from '../api/analyze'
import { AppFrame, Badge, Button, Card, Eyebrow, StateRow } from '../components'

const ACCEPTED_EXTENSIONS = ['.yml', '.yaml']

type AnalyzeStatus =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; report: AnalysisResponse }
  | { kind: 'error'; message: string }

export function Analyzer() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [status, setStatus] = useState<AnalyzeStatus>({ kind: 'idle' })

  const canAnalyze = file !== null && validationError === null && status.kind !== 'loading'
  const issueCount = useMemo(() => {
    if (status.kind !== 'success') return 0
    return status.report.dimensions.reduce((total, dimension) => total + dimension.missingPractices.length, 0)
  }, [status])

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null
    setStatus({ kind: 'idle' })
    setFile(selected)
    setValidationError(validateFile(selected))
  }

  function clearFile() {
    setFile(null)
    setValidationError(null)
    setStatus({ kind: 'idle' })
    if (inputRef.current) inputRef.current.value = ''
  }

  async function analyze() {
    const error = validateFile(file)
    setValidationError(error)
    if (!file || error) return

    setStatus({ kind: 'loading' })
    try {
      const report = await analyzePipeline(file)
      setStatus({ kind: 'success', report })
    } catch (err) {
      setStatus({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Pipeline analysis failed.',
      })
    }
  }

  return (
    <AppFrame>
      <section aria-labelledby="analyzer-title" className="analyzer-band">
        <header className="page-header">
          <Eyebrow>Pipeline analyzer</Eyebrow>
          <h1 id="analyzer-title">Upload a pipeline and review its CI/CD maturity.</h1>
          <p>
            Analyze GitHub Actions or GitLab CI YAML files against build, test, security,
            artifacts, deployment, notification, and code quality practices.
          </p>
        </header>

        <div className="analyzer-layout">
          <Card as="section" className="upload-panel">
            <div>
              <Eyebrow>Upload</Eyebrow>
              <h2>Pipeline file</h2>
              <p>Use a single .yml or .yaml file. The backend performs provider detection and scoring.</p>
            </div>

            <label className={validationError ? 'file-drop file-drop--error' : 'file-drop'} htmlFor="pipeline-file">
              <span>{file ? file.name : 'Choose a pipeline YAML file'}</span>
              <small>{file ? formatFileSize(file.size) : 'GitHub Actions and GitLab CI are supported.'}</small>
              <input
                accept=".yml,.yaml"
                id="pipeline-file"
                onChange={selectFile}
                ref={inputRef}
                type="file"
              />
            </label>

            <div className="upload-actions">
              <Button disabled={!canAnalyze} onClick={analyze}>
                {status.kind === 'loading' ? 'Analyzing...' : 'Analyze pipeline'}
              </Button>
              <Button disabled={!file && status.kind === 'idle'} onClick={clearFile} variant="secondary">
                Clear
              </Button>
            </div>

            {validationError && (
              <StateRow detail={validationError} icon="!" label="File cannot be analyzed" tone="error" />
            )}

            {status.kind === 'loading' && (
              <StateRow detail="Calling /api/analyze with the selected YAML file." label="Analyzing pipeline" tone="loading" />
            )}
            {status.kind === 'error' && (
              <StateRow detail={status.message} icon="!" label="Analysis failed" tone="error" />
            )}
          </Card>

          {status.kind === 'success' ? (
            <ReportPanel issueCount={issueCount} report={status.report} />
          ) : (
            <Card as="section" className="report-empty">
              <Eyebrow>Report</Eyebrow>
              <h2>No analysis yet</h2>
              <p>
                The report will show an overall maturity rating, dimension scores, detected practices,
                and the missing practices that need attention.
              </p>
            </Card>
          )}
        </div>
      </section>
    </AppFrame>
  )
}

function ReportPanel({ issueCount, report }: { issueCount: number; report: AnalysisResponse }) {
  const [openSection, setOpenSection] = useState<string | null>('overall')

  function toggleSection(section: string) {
    setOpenSection((current) => (current === section ? null : section))
  }

  return (
    <section aria-label="Pipeline analysis report" className="report-panel">
      <Card as="section" className={openSection === 'overall' ? 'accordion-section accordion-section--open' : 'accordion-section'}>
        <button
          aria-controls="overall-report-details"
          aria-expanded={openSection === 'overall'}
          className="accordion-trigger score-card"
          onClick={() => toggleSection('overall')}
          type="button"
        >
          <div>
            <Eyebrow>Overall rating</Eyebrow>
            <div className="score-card__value">{formatScore(report.overallScore)}</div>
          </div>
          <div className="score-card__summary">
            <div className="score-card__meta">
              <Badge>{formatProvider(report.provider)}</Badge>
              <Badge>Level {report.overallLevel}</Badge>
              <Badge>{formatLabel(report.overallStatus)}</Badge>
              <Badge>{formatLabel(report.overallConfidence)} confidence</Badge>
            </div>
            <p>{issueCount === 0 ? 'No missing practices were reported.' : `${issueCount} issue${issueCount === 1 ? '' : 's'} found across analyzed dimensions.`}</p>
          </div>
          <span className="accordion-chevron" aria-hidden="true">{openSection === 'overall' ? 'Hide' : 'Show'}</span>
        </button>
        {openSection === 'overall' && (
          <div className="accordion-content" id="overall-report-details">
            <OverallDetail issueCount={issueCount} report={report} />
          </div>
        )}
      </Card>

      <div className="dimension-grid">
        {report.dimensions.map((dimension) => (
          <DimensionAccordion
            dimension={dimension}
            key={dimension.dimension}
            onToggle={() => toggleSection(dimension.dimension)}
            open={openSection === dimension.dimension}
          />
        ))}
      </div>
    </section>
  )
}

function DimensionAccordion({
  dimension,
  onToggle,
  open,
}: {
  dimension: DimensionAnalysis
  onToggle: () => void
  open: boolean
}) {
  const contentId = `${dimension.dimension}-details`

  return (
    <Card as="section" className={open ? 'accordion-section accordion-section--open' : 'accordion-section'}>
      <button
        aria-controls={contentId}
        aria-expanded={open}
        className="accordion-trigger dimension-card"
        onClick={onToggle}
        type="button"
      >
        <div className="dimension-card__head">
          <div>
            <h3>{formatDimension(dimension.dimension)}</h3>
            <p>Level {dimension.level} · {formatLabel(dimension.status)}</p>
          </div>
          <strong>{formatScore(dimension.score)}</strong>
        </div>
        <div aria-hidden="true" className="score-bar">
          <span style={{ width: formatScore(dimension.score) }} />
        </div>
        <div className="dimension-card__details">
          <span>{dimension.detectedPractices.length} detected</span>
          <span>{dimension.missingPractices.length} missing</span>
          <span>{formatLabel(dimension.confidence)} confidence</span>
        </div>
        <span className="accordion-chevron" aria-hidden="true">{open ? 'Hide' : 'Show'}</span>
      </button>
      {open && (
        <div className="accordion-content" id={contentId}>
          <DimensionDetail dimension={dimension} />
        </div>
      )}
    </Card>
  )
}

function OverallDetail({ issueCount, report }: { issueCount: number; report: AnalysisResponse }) {
  return (
    <>
      <div className="section-title-row">
        <div>
          <Eyebrow>Dimension overview</Eyebrow>
          <h2>Report breakdown</h2>
        </div>
        <Badge>{issueCount} open</Badge>
      </div>
      <div className="overview-grid">
        {report.dimensions.map((dimension) => (
          <div className="overview-row" key={dimension.dimension}>
            <span>{formatDimension(dimension.dimension)}</span>
            <strong>{formatScore(dimension.score)}</strong>
            <small>{dimension.missingPractices.length} missing</small>
          </div>
        ))}
      </div>
    </>
  )
}

function DimensionDetail({ dimension }: { dimension: DimensionAnalysis }) {
  return (
    <div className="detail-columns">
      <div className="issue-group">
        <h3>Missing practices</h3>
        {dimension.missingPractices.length > 0 ? (
          <ul>
            {dimension.missingPractices.map((practice) => (
              <li key={practice}>{practice}</li>
            ))}
          </ul>
        ) : (
          <StateRow detail="No missing practices were reported for this dimension." icon="✓" label="No issues found" tone="success" />
        )}
      </div>

      <div className="detected-group">
        <h3>Detected practices</h3>
        {dimension.detectedPractices.length > 0 ? (
          <ul>
            {dimension.detectedPractices.map((practice) => (
              <li key={`${practice.practice}-${practice.location}-${practice.evidence}`}>
                <strong>{practice.practice}</strong>
                <span>{practice.evidence}</span>
                {practice.location && <code>{practice.location}</code>}
              </li>
            ))}
          </ul>
        ) : (
          <StateRow detail="No detected practices were reported for this dimension." label="No evidence found" tone="empty" />
        )}
      </div>
    </div>
  )
}

function validateFile(file: File | null): string | null {
  if (!file) return null
  const normalizedName = file.name.toLowerCase()
  if (!ACCEPTED_EXTENSIONS.some((extension) => normalizedName.endsWith(extension))) {
    return 'Upload a .yml or .yaml pipeline file.'
  }
  return null
}

function formatScore(score: number): string {
  return `${Math.round(score * 100)}%`
}

function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatProvider(provider: string): string {
  if (provider === 'github-actions') return 'GitHub Actions'
  if (provider === 'gitlab-ci') return 'GitLab CI'
  return formatLabel(provider)
}

function formatDimension(dimension: string): string {
  return dimension
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatLabel(value: string): string {
  return value
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
