import { useEffect, useMemo, useState } from 'react'
import { AppFrame, Button, StateRow, TextInput, Tooltip } from '../components'
import { StackIcon } from '../components/wizard/StackIcon'
import {
  createInitJob,
  downloadBlob,
  downloadInitJob,
  getInitCatalog,
  getInitJob,
  type InitCatalog,
  type InitJob,
  type MaturityPreset,
  type PipelineCatalogOption,
  type StackCatalogOption,
} from '../api/init'

const PROJECT_NAME_PATTERN = /^[a-z][a-z0-9-]*[a-z0-9]$/

type WizardState = {
  backend: string
  backendRuntime: string
  backendVersion: string
  frontend: string
  frontendRuntime: string
  frontendVersion: string
  pipeline: string
  pipelineMaturity: string
  projectName: string
  includeDocker: boolean
}

type GenerationStatus =
  | { kind: 'idle' }
  | { kind: 'loading'; job?: InitJob }
  | { kind: 'success'; job: InitJob }
  | { kind: 'error'; message: string; job?: InitJob }

const initialState: WizardState = {
  backend: '',
  backendRuntime: '',
  backendVersion: '',
  frontend: '',
  frontendRuntime: '',
  frontendVersion: '',
  pipeline: '',
  pipelineMaturity: '',
  projectName: '',
  includeDocker: false,
}

export function Initializer() {
  const [catalog, setCatalog] = useState<InitCatalog | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [state, setState] = useState<WizardState>(initialState)
  const [status, setStatus] = useState<GenerationStatus>({ kind: 'idle' })

  useEffect(() => {
    let mounted = true

    getInitCatalog()
      .then((nextCatalog) => {
        if (!mounted) return
        setCatalog(nextCatalog)
        setState((prev) => withCatalogDefaults(prev, nextCatalog))
      })
      .catch((err) => {
        if (!mounted) return
        setCatalogError(err instanceof Error ? err.message : 'Could not load initializer catalog.')
      })

    return () => {
      mounted = false
    }
  }, [])

  const projectNameError = useMemo(() => validateProjectName(state.projectName), [state.projectName])

  const canGenerate = useMemo(() => {
    if (!catalog) return false
    if (status.kind === 'loading') return false
    return Boolean(
      state.frontend &&
        state.frontendVersion &&
        state.frontendRuntime &&
        state.backend &&
        state.backendVersion &&
        state.backendRuntime &&
        state.pipeline &&
        state.pipelineMaturity &&
        state.projectName &&
        projectNameError === null,
    )
  }, [catalog, state, projectNameError, status.kind])

  function update<K extends keyof WizardState>(key: K, value: WizardState[K]) {
    setStatus((prev) => (prev.kind === 'error' ? { kind: 'idle' } : prev))
    setState((prev) => {
      const next = { ...prev, [key]: value }
      if (!catalog) return next

      if (key === 'frontend') return withStackDefaults(next, catalog.frontends, 'frontend')
      if (key === 'frontendVersion') return withRuntimeDefault(next, catalog.frontends, 'frontend')
      if (key === 'backend') return withStackDefaults(next, catalog.backends, 'backend')
      if (key === 'backendVersion') return withRuntimeDefault(next, catalog.backends, 'backend')
      if (key === 'pipelineMaturity') {
        const maturity = catalog.maturityPresets.find((preset) => preset.id === value)
        if (maturity?.dockerRequired) return { ...next, includeDocker: true }
      }
      return next
    })
  }

  function startOver() {
    setState(catalog ? withCatalogDefaults(initialState, catalog) : initialState)
    setStatus({ kind: 'idle' })
  }

  async function generate() {
    if (!catalog) return
    setStatus({ kind: 'loading' })
    try {
      const created = await createInitJob({
        projectName: state.projectName,
        frontend: state.frontend,
        frontendVersion: state.frontendVersion,
        frontendRuntime: state.frontendRuntime,
        backend: state.backend,
        backendVersion: state.backendVersion,
        backendRuntime: state.backendRuntime,
        pipeline: state.pipeline,
        pipelineMaturity: state.pipelineMaturity,
        includeDocker: state.includeDocker,
      })
      setStatus({ kind: 'loading', job: created })

      let current = created
      while (current.status === 'queued' || current.status === 'running') {
        await delay(1400)
        current = await getInitJob(created.jobId)
        setStatus({ kind: 'loading', job: current })
      }

      if (current.status === 'succeeded') {
        setStatus({ kind: 'success', job: current })
        return
      }

      setStatus({
        kind: 'error',
        job: current,
        message: current.errorMessage || 'Generation failed.',
      })
    } catch (err) {
      setStatus({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Generation failed.',
      })
    }
  }

  async function downloadGeneratedJob(job: InitJob) {
    try {
      const blob = await downloadInitJob(job.jobId)
      downloadBlob(blob, `${state.projectName}.zip`)
    } catch (err) {
      setStatus({
        kind: 'error',
        job,
        message: err instanceof Error ? err.message : 'Download failed.',
      })
    }
  }

  return (
    <AppFrame>
      <section aria-labelledby="wizard-title" className="init-band">
        <header className="init-hero">
          <span className="init-hero__kicker">Project initializer</span>
          <h1 id="wizard-title">Scaffold a new project in four steps.</h1>
          <p>
            Pick a stack, runtime, and pipeline. Scaffy queues the build and hands you back a ready-to-run
            ZIP — no boilerplate hunting.
          </p>
        </header>

        {catalogError && (
          <StateRow detail={catalogError} icon="!" label="Catalog unavailable" tone="error" />
        )}

        {!catalog && !catalogError && (
          <StateRow
            detail="Loading supported stack and version presets."
            label="Loading catalog"
            tone="loading"
          />
        )}

        {catalog && (
          <div className="init-layout">
            <div className="init-config">
              <WizardStep
                index={1}
                title="Project details"
                hint="What should we call your repository?"
              >
                <ProjectDetailsStep
                  catalog={catalog}
                  error={projectNameError}
                  state={state}
                  update={update}
                />
              </WizardStep>

              <WizardStep
                index={2}
                title="Frontend"
                hint="Pick a UI framework, version, and runtime."
              >
                <StackPresetGroup
                  options={catalog.frontends}
                  selectedId={state.frontend}
                  selectedRuntimeId={state.frontendRuntime}
                  selectedVersionId={state.frontendVersion}
                  onSelect={(id) => update('frontend', id)}
                  onRuntimeSelect={(id) => update('frontendRuntime', id)}
                  onVersionSelect={(id) => update('frontendVersion', id)}
                  group="frontend"
                />
              </WizardStep>

              <WizardStep
                index={3}
                title="Backend"
                hint="Choose the API framework that fits your team."
              >
                <StackPresetGroup
                  options={catalog.backends}
                  selectedId={state.backend}
                  selectedRuntimeId={state.backendRuntime}
                  selectedVersionId={state.backendVersion}
                  onSelect={(id) => update('backend', id)}
                  onRuntimeSelect={(id) => update('backendRuntime', id)}
                  onVersionSelect={(id) => update('backendVersion', id)}
                  group="backend"
                />
              </WizardStep>

              <WizardStep
                index={4}
                title="CI / CD pipeline"
                hint="Choose the provider and how much delivery discipline Scaffy should generate."
              >
                <PipelineStep catalog={catalog} state={state} update={update} />
              </WizardStep>
            </div>

            <aside className="init-summary">
              <ReviewPanel
                canGenerate={canGenerate}
                catalog={catalog}
                onGenerate={generate}
                onDownload={downloadGeneratedJob}
                onRetry={generate}
                onStartOver={startOver}
                state={state}
                status={status}
              />
            </aside>
          </div>
        )}
      </section>
    </AppFrame>
  )
}

type WizardStepProps = {
  index: number
  title: string
  hint: string
  children: React.ReactNode
}

function WizardStep({ index, title, hint, children }: WizardStepProps) {
  return (
    <section className="init-step">
      <header className="init-step__head">
        <span className="init-step__index">{String(index).padStart(2, '0')}</span>
        <div>
          <h2 className="init-step__title">{title}</h2>
          <p className="init-step__hint">{hint}</p>
        </div>
      </header>
      <div className="init-step__body">{children}</div>
    </section>
  )
}

function validateProjectName(name: string): string | null {
  if (!name) return null
  if (name.length < 2) return 'Must be at least 2 characters.'
  if (name.length > 64) return 'Must be 64 characters or fewer.'
  if (!PROJECT_NAME_PATTERN.test(name))
    return 'Lowercase letters, digits, hyphens only. Must start with a letter and end with a letter or digit.'
  return null
}

type StepProps = {
  state: WizardState
  update: <K extends keyof WizardState>(key: K, value: WizardState[K]) => void
}

type CatalogStepProps = StepProps & { catalog: InitCatalog }

type StackPresetGroupProps = {
  options: StackCatalogOption[]
  selectedId: string
  selectedVersionId: string
  selectedRuntimeId: string
  onSelect: (id: string) => void
  onVersionSelect: (id: string) => void
  onRuntimeSelect: (id: string) => void
  group: 'frontend' | 'backend'
}

function StackPresetGroup({
  options,
  selectedId,
  selectedVersionId,
  selectedRuntimeId,
  onSelect,
  onVersionSelect,
  onRuntimeSelect,
  group,
}: StackPresetGroupProps) {
  const selected = findById(options, selectedId)
  const selectedVersion = findById(selected?.versions ?? [], selectedVersionId)

  return (
    <div className="stack-group">
      <div className="stack-cards" role="radiogroup" aria-label={`${group} framework`}>
        {options.map((option) => {
          const isSelected = selectedId === option.id
          const defaultVersion =
            findById(option.versions, option.defaultVersionId) ?? option.versions[0]
          const defaultRuntime =
            findById(defaultVersion?.runtimes ?? [], defaultVersion?.defaultRuntimeId ?? '') ??
            defaultVersion?.runtimes[0]

          return (
            <button
              aria-checked={isSelected}
              className={`stack-card${isSelected ? ' stack-card--selected' : ''}`}
              key={option.id}
              onClick={() => onSelect(option.id)}
              role="radio"
              type="button"
            >
              <span className="stack-card__icon" aria-hidden="true">
                <StackIcon id={option.id} />
              </span>
              <span className="stack-card__body">
                <span className="stack-card__name">{option.name}</span>
                <span className="stack-card__meta">
                  {[defaultVersion?.label, defaultRuntime?.label].filter(Boolean).join(' · ')}
                </span>
              </span>
              <span className="stack-card__mark" aria-hidden="true">
                {isSelected ? <CheckIcon /> : null}
              </span>
            </button>
          )
        })}
      </div>

      {selected && selectedVersion && (
        <div className="stack-detail">
          <p className="stack-detail__copy">{selected.description}</p>
          <ChipRow
            label="Version"
            options={selected.versions.map((v) => ({ id: v.id, label: v.label }))}
            selectedId={selectedVersionId}
            onSelect={onVersionSelect}
            ariaLabel={`${group} version`}
          />
          <ChipRow
            label="Runtime"
            options={selectedVersion.runtimes.map((r) => ({
              id: r.id,
              label: r.label,
              lts: r.lts,
            }))}
            selectedId={selectedRuntimeId}
            onSelect={onRuntimeSelect}
            ariaLabel={`${group} runtime`}
          />
        </div>
      )}
    </div>
  )
}

type ChipRowProps = {
  label: string
  options: { id: string; label: string; lts?: boolean }[]
  selectedId: string
  onSelect: (id: string) => void
  ariaLabel: string
}

function ChipRow({ label, options, selectedId, onSelect, ariaLabel }: ChipRowProps) {
  return (
    <div className="chip-row">
      <span className="chip-row__label">{label}</span>
      <div className="chip-row__chips" role="radiogroup" aria-label={ariaLabel}>
        {options.map((option) => {
          const isSelected = option.id === selectedId
          return (
            <button
              aria-checked={isSelected}
              className={`chip${isSelected ? ' chip--selected' : ''}`}
              key={option.id}
              onClick={() => onSelect(option.id)}
              role="radio"
              type="button"
            >
              {option.label}
              {option.lts && (
                <span className="chip__badge" aria-label="Long-term support">
                  LTS
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function PipelineStep({ catalog, state, update }: CatalogStepProps) {
  return (
    <div className="pipeline-step">
      <div className="pipeline-cards" role="radiogroup" aria-label="Pipeline provider">
        {catalog.pipelines.map((option) => {
          const isSelected = state.pipeline === option.id
          return (
            <button
              aria-checked={isSelected}
              className={`pipeline-card${isSelected ? ' pipeline-card--selected' : ''}`}
              key={option.id}
              onClick={() => update('pipeline', option.id)}
              role="radio"
              type="button"
            >
              <span className="pipeline-card__icon" aria-hidden="true">
                <StackIcon id={option.id} />
              </span>
              <span className="pipeline-card__body">
                <span className="pipeline-card__name">{option.name}</span>
                <span className="pipeline-card__desc">{option.description}</span>
              </span>
              <span className="pipeline-card__mark" aria-hidden="true">
                {isSelected ? <CheckIcon /> : null}
              </span>
            </button>
          )
        })}
      </div>

      <div className="maturity-section">
        <div className="maturity-section__head">
          <span className="maturity-section__label">Maturity level</span>
          <span className="maturity-section__hint">
            How much delivery discipline Scaffy should generate.
          </span>
        </div>

        <div className="maturity-picker" role="radiogroup" aria-label="Pipeline maturity level">
          {catalog.maturityPresets.map((preset) => {
            const isSelected = state.pipelineMaturity === preset.id
            const name = preset.label.replace(/^L\d\s+/, '')
            return (
              <button
                aria-checked={isSelected}
                className={`maturity-card${isSelected ? ' maturity-card--selected' : ''}`}
                key={preset.id}
                onClick={() => update('pipelineMaturity', preset.id)}
                role="radio"
                type="button"
              >
                <span className="maturity-card__bars" aria-hidden="true">
                  {[1, 2, 3, 4].map((i) => (
                    <span
                      className={`maturity-card__bar${i <= preset.level ? ' maturity-card__bar--on' : ''}`}
                      key={i}
                    />
                  ))}
                </span>
                <span className="maturity-card__body">
                  <span className="maturity-card__title-row">
                    <span className="maturity-card__name">
                      <span className="maturity-card__level">L{preset.level}</span>
                      {name}
                    </span>
                    {preset.dockerRequired && (
                      <span className="maturity-card__badge" title="Requires Docker">
                        Docker
                      </span>
                    )}
                  </span>
                  <span className="maturity-card__desc">{preset.description}</span>
                </span>
                <span className="maturity-card__mark" aria-hidden="true">
                  {isSelected ? <CheckIcon /> : null}
                </span>
              </button>
            )
          })}
        </div>

        <MaturityLockedCard />
      </div>
    </div>
  )
}

function MaturityLockedCard() {
  return (
    <Tooltip
      side="top"
      align="start"
      content={
        <div className="tooltip__body">
          <strong>Why no L5 yet?</strong>
          <p>
            Honest L5 needs choices Scaffy doesn&apos;t ask for: deployment target
            (Kubernetes, Vercel, ECS…), registry, IaC tool (Terraform, Pulumi,
            Helm), rollout strategy (canary, blue-green, rolling), secrets model,
            and shared reusable workflows.
          </p>
          <p>
            Generating it without those would produce YAML that &quot;looks mature&quot;
            but doesn&apos;t actually run. It will land as a separate advanced flow.
          </p>
        </div>
      }
    >
      <div
        aria-disabled="true"
        className="maturity-locked"
        role="note"
        tabIndex={0}
      >
        <span className="maturity-locked__bars" aria-hidden="true">
          {[1, 2, 3, 4, 5].map((i) => (
            <span
              className={`maturity-card__bar${i <= 5 ? ' maturity-card__bar--on' : ''}`}
              key={i}
            />
          ))}
        </span>
        <div className="maturity-locked__body">
          <div className="maturity-locked__title-row">
            <span className="maturity-card__name">
              <span className="maturity-card__level">L5</span>
              Advanced Pipeline
            </span>
            <span className="maturity-locked__chip">Coming later</span>
          </div>
          <p className="maturity-locked__desc">
            Canary / blue-green rollouts, policy-as-code, IaC, and reusable org
            workflows — hover for why this isn&apos;t available yet.
          </p>
        </div>
      </div>
    </Tooltip>
  )
}

type ProjectDetailsProps = StepProps & { catalog: InitCatalog; error: string | null }

function ProjectDetailsStep({ catalog, error, state, update }: ProjectDetailsProps) {
  const maturity = catalog.maturityPresets.find((preset) => preset.id === state.pipelineMaturity)
  const dockerLocked = Boolean(maturity?.dockerRequired)
  const dockerChecked = dockerLocked || state.includeDocker
  const dockerHint = dockerLocked
    ? `${maturity?.label ?? 'This maturity level'} requires Docker, so it's enabled automatically.`
    : 'Adds Dockerfiles and docker-compose. L3 and L4 include this automatically.'

  return (
    <div className="project-details">
      <div className="project-details__field">
        <label htmlFor="project-name">Project name</label>
        <TextInput
          aria-describedby="project-name-help"
          aria-invalid={error !== null}
          autoComplete="off"
          id="project-name"
          onChange={(event) => update('projectName', event.target.value)}
          placeholder="my-awesome-app"
          value={state.projectName}
        />
        <p
          className={`project-details__hint${error ? ' project-details__hint--error' : ''}`}
          id="project-name-help"
        >
          {error ??
            'Lowercase letters, digits, hyphens · 2–64 characters · must start with a letter.'}
        </p>
      </div>

      <label
        className={`docker-toggle${dockerLocked ? ' docker-toggle--locked' : ''}`}
        htmlFor="include-docker"
      >
        <span className="docker-toggle__copy">
          <span className="docker-toggle__title">
            Include Docker support
            {dockerLocked && <span className="docker-toggle__lock">Locked by {maturity?.label}</span>}
          </span>
          <span className="docker-toggle__desc">{dockerHint}</span>
        </span>
        <input
          checked={dockerChecked}
          disabled={dockerLocked}
          id="include-docker"
          onChange={(event) => update('includeDocker', event.target.checked)}
          type="checkbox"
        />
        <span className="docker-toggle__switch" aria-hidden="true" />
      </label>
    </div>
  )
}

type ReviewPanelProps = {
  canGenerate: boolean
  catalog: InitCatalog
  onDownload: (job: InitJob) => void
  onGenerate: () => void
  onRetry: () => void
  onStartOver: () => void
  state: WizardState
  status: GenerationStatus
}

function ReviewPanel({
  canGenerate,
  catalog,
  onDownload,
  onGenerate,
  onRetry,
  onStartOver,
  state,
  status,
}: ReviewPanelProps) {
  const frontend = findById(catalog.frontends, state.frontend)
  const frontendVersion = findById(frontend?.versions ?? [], state.frontendVersion)
  const frontendRuntime = findById(frontendVersion?.runtimes ?? [], state.frontendRuntime)
  const backend = findById(catalog.backends, state.backend)
  const backendVersion = findById(backend?.versions ?? [], state.backendVersion)
  const backendRuntime = findById(backendVersion?.runtimes ?? [], state.backendRuntime)
  const pipeline = findById<PipelineCatalogOption>(catalog.pipelines, state.pipeline)
  const maturity = findById<MaturityPreset>(catalog.maturityPresets, state.pipelineMaturity)
  const dockerIncluded = state.includeDocker || Boolean(maturity?.dockerRequired)

  return (
    <div className="review">
      <div className="review__head">
        <span className="review__eyebrow">Summary</span>
        <div className="review__name">{state.projectName || 'unnamed-project'}</div>
      </div>

      <ul className="review__rows">
        <ReviewRow label="Frontend" iconId={frontend?.id}>
          {frontend ? (
            <>
              <strong>{frontend.name}</strong>
              <span>
                {[frontendVersion?.label, frontendRuntime?.label].filter(Boolean).join(' · ') || '—'}
              </span>
            </>
          ) : (
            <span className="review__placeholder">Not selected</span>
          )}
        </ReviewRow>

        <ReviewRow label="Backend" iconId={backend?.id}>
          {backend ? (
            <>
              <strong>{backend.name}</strong>
              <span>
                {[backendVersion?.label, backendRuntime?.label].filter(Boolean).join(' · ') || '—'}
              </span>
            </>
          ) : (
            <span className="review__placeholder">Not selected</span>
          )}
        </ReviewRow>

        <ReviewRow label="Pipeline" iconId={pipeline?.id}>
          {pipeline ? <strong>{pipeline.name}</strong> : <span className="review__placeholder">Not selected</span>}
        </ReviewRow>

        <li className="review__row review__row--inline">
          <span className="review__label">Maturity</span>
          <span className="review__pill review__pill--on">
            {maturity?.label ?? 'L2 Basic CI'}
          </span>
        </li>

        <li className="review__row review__row--inline">
          <span className="review__label">Docker</span>
          <span className={`review__pill${dockerIncluded ? ' review__pill--on' : ''}`}>
            {dockerIncluded ? 'Included' : 'Off'}
          </span>
        </li>
      </ul>

      <div className="review__actions">
        {status.kind === 'success' ? (
          <>
            <Button onClick={() => onDownload(status.job)} variant="download">
              Download ZIP
            </Button>
            <Button onClick={onStartOver} variant="secondary">
              Start over
            </Button>
          </>
        ) : status.kind === 'error' ? (
          <>
            <Button disabled={!canGenerate} onClick={onRetry} variant="download">
              Retry generation
            </Button>
            <Button onClick={onStartOver} variant="secondary">
              Reset
            </Button>
          </>
        ) : (
          <Button disabled={!canGenerate} onClick={onGenerate} variant="download">
            {status.kind === 'loading' ? 'Generating…' : 'Generate project'}
          </Button>
        )}
      </div>

      <GenerationPanel status={status} />
    </div>
  )
}

type ReviewRowProps = {
  label: string
  iconId?: string
  children: React.ReactNode
}

function ReviewRow({ label, iconId, children }: ReviewRowProps) {
  return (
    <li className="review__row">
      <span className="review__label">{label}</span>
      <span className="review__value">
        {iconId ? (
          <span className="review__icon" aria-hidden="true">
            <StackIcon id={iconId} />
          </span>
        ) : (
          <span className="review__icon review__icon--empty" aria-hidden="true" />
        )}
        <span className="review__value-text">{children}</span>
      </span>
    </li>
  )
}

type GenerationPanelProps = {
  status: GenerationStatus
}

function GenerationPanel({ status }: GenerationPanelProps) {
  if (status.kind === 'idle') {
    return (
      <div className="gen gen--idle">
        <p className="gen__hint">
          When you're ready, we'll queue a worker, stream the build log, and surface the ZIP.
        </p>
      </div>
    )
  }

  const job = status.kind === 'loading' || status.kind === 'success' || status.kind === 'error'
    ? status.job
    : undefined
  const error = status.kind === 'error' ? formatGenerationError(status.message) : null
  const progress = error?.summary
    ?? job?.progress
    ?? (status.kind === 'loading' ? 'Waiting for the generator worker…' : 'Artifact is ready.')
  const percent = generationPercent(status)

  return (
    <div className={`gen gen--${status.kind}`}>
      <div className="gen__head">
        <span className={`gen__dot gen__dot--${status.kind}`} aria-hidden="true" />
        <div>
          <strong>{generationTitle(status)}</strong>
          <p>{progress}</p>
        </div>
      </div>

      <div className="gen__progress" aria-label="Generation progress">
        <span style={{ width: `${percent}%` }} />
      </div>

      {error?.details && (
        <details className="gen__error">
          <summary>Technical details</summary>
          <pre>{error.details}</pre>
        </details>
      )}

      {job?.logs?.length ? <GenerationLog logs={job.logs} /> : null}

      <dl className="gen__meta">
        <div>
          <dt>Job</dt>
          <dd>{job?.jobId ?? '—'}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{job?.status ?? status.kind}</dd>
        </div>
        <div>
          <dt>Download</dt>
          <dd>{job?.downloadAvailable ? 'Ready' : 'Pending'}</dd>
        </div>
      </dl>
    </div>
  )
}

function GenerationLog({ logs }: { logs: InitJob['logs'] }) {
  return (
    <div className="gen__log" aria-label="Generation log">
      <div className="gen__log-bar">
        <span>Build log</span>
        <span>{logs.length} lines</span>
      </div>
      <pre>
        {logs.map((line) => (
          <span className={`gen__log-line gen__log-line--${line.stream}`} key={line.id}>
            <span className="gen__log-stream">{line.stream}</span>
            {line.message}
            {'\n'}
          </span>
        ))}
      </pre>
    </div>
  )
}

function generationTitle(status: GenerationStatus): string {
  if (status.kind === 'loading') {
    if (status.job?.status === 'queued') return 'Queued for generation'
    return 'Generating project'
  }
  if (status.kind === 'success') return 'Artifact ready'
  if (status.kind === 'error') return 'Generation failed'
  return 'Ready to generate'
}

function generationPercent(status: GenerationStatus): number {
  if (status.kind === 'success') return 100
  if (status.kind === 'error') return 100
  if (status.kind !== 'loading') return 0
  if (status.job?.status === 'running') return 62
  if (status.job?.status === 'queued') return 18
  return 8
}

function formatGenerationError(message: string): { summary: string; details?: string } {
  const trimmed = message.trim()
  if (!trimmed) return { summary: 'Generation failed.' }

  const knownSummary = knownGenerationErrorSummary(trimmed)
  if (knownSummary) {
    return { summary: knownSummary, details: trimmed }
  }

  const [firstLine, ...rest] = trimmed.split(/\n+/)
  const summary = compactSummary(firstLine)
  const details = rest.join('\n').trim() || (trimmed.length > summary.length ? trimmed : '')
  return details ? { summary, details } : { summary }
}

function knownGenerationErrorSummary(message: string): string | null {
  if (/_cacache|npm error code EACCES|npm error code EEXIST|permission denied, rename/i.test(message)) {
    return 'npm failed while writing its package cache. Restart the generator so it uses the isolated per-job cache, then run generation again.'
  }
  if (/ENOTFOUND|ECONNRESET|ETIMEDOUT|network request|registry\.npmjs\.org/i.test(message)) {
    return 'The generator could not reach the npm registry. Check network access from the generator process.'
  }
  if (/command not found|ENOENT/i.test(message)) {
    return 'The generator host is missing a required CLI or runtime for this stack.'
  }
  return null
}

function compactSummary(value: string): string {
  const cleaned = value
    .replace(/\s+/g, ' ')
    .replace(/^Command failed with exit code \d+:\s*/i, 'Command failed: ')
    .trim()
  return cleaned.length > 180 ? `${cleaned.slice(0, 177)}…` : cleaned
}

function withCatalogDefaults(state: WizardState, catalog: InitCatalog): WizardState {
  const frontend = catalog.frontends[0]
  const backend = catalog.backends[0]
  const pipeline = catalog.pipelines[0]
  const maturity = catalog.maturityPresets.find((preset) => preset.id === 'l2') ?? catalog.maturityPresets[0]
  const next = {
    ...state,
    frontend: state.frontend || frontend?.id || '',
    backend: state.backend || backend?.id || '',
    pipeline: state.pipeline || pipeline?.id || '',
    pipelineMaturity: state.pipelineMaturity || maturity?.id || '',
  }
  return withStackDefaults(withStackDefaults(next, catalog.frontends, 'frontend'), catalog.backends, 'backend')
}

function withStackDefaults(
  state: WizardState,
  options: StackCatalogOption[],
  kind: 'frontend' | 'backend',
): WizardState {
  const stack = findById(options, state[kind])
  const versionKey = `${kind}Version` as const
  const versionId = stack?.versions.some((version) => version.id === state[versionKey])
    ? state[versionKey]
    : stack?.defaultVersionId || stack?.versions[0]?.id || ''
  const next = { ...state, [versionKey]: versionId }
  return withRuntimeDefault(next, options, kind)
}

function withRuntimeDefault(
  state: WizardState,
  options: StackCatalogOption[],
  kind: 'frontend' | 'backend',
): WizardState {
  const stack = findById(options, state[kind])
  const version = findById(stack?.versions ?? [], state[`${kind}Version`])
  const runtimeKey = `${kind}Runtime` as const
  const runtimeId = version?.runtimes.some((runtime) => runtime.id === state[runtimeKey])
    ? state[runtimeKey]
    : version?.defaultRuntimeId || version?.runtimes[0]?.id || ''
  return { ...state, [runtimeKey]: runtimeId }
}

function findById<T extends { id: string }>(items: T[], id: string): T | undefined {
  return items.find((item) => item.id === id)
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
      <path
        d="M3.5 8.5l3 3 6-7"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
