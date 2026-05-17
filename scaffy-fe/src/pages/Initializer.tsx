import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Card, Eyebrow, StateRow, TextInput } from '../components'
import { ChoiceCard } from '../components/wizard/ChoiceCard'
import { StepIndicator } from '../components/wizard/StepIndicator'
import { downloadBlob, initProject } from '../api/init'
import { BACKENDS, FRONTENDS, PIPELINES, findOption } from '../catalog'

const PROJECT_NAME_PATTERN = /^[a-z][a-z0-9-]*[a-z0-9]$/
const STEPS = ['Stack', 'Pipeline', 'Configuration', 'Review']
const TOTAL_STEPS = STEPS.length

type WizardState = {
  backend: string
  frontend: string
  pipeline: string
  projectName: string
  includeDocker: boolean
}

type GenerationStatus =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success' }
  | { kind: 'error'; message: string }

const initialState: WizardState = {
  backend: 'spring-boot',
  frontend: 'angular',
  pipeline: '',
  projectName: '',
  includeDocker: false,
}

export function Initializer() {
  const [step, setStep] = useState(1)
  const [state, setState] = useState<WizardState>(initialState)
  const [status, setStatus] = useState<GenerationStatus>({ kind: 'idle' })

  const projectNameError = useMemo(() => validateProjectName(state.projectName), [state.projectName])

  const canAdvance = useMemo(() => {
    if (step === 1) return Boolean(state.frontend && state.backend)
    if (step === 2) return Boolean(state.pipeline)
    if (step === 3) return Boolean(state.projectName) && projectNameError === null
    return true
  }, [step, state, projectNameError])

  function update<K extends keyof WizardState>(key: K, value: WizardState[K]) {
    setState((prev) => ({ ...prev, [key]: value }))
  }

  function next() {
    if (canAdvance && step < TOTAL_STEPS) setStep(step + 1)
  }

  function back() {
    if (step > 1) setStep(step - 1)
    if (status.kind === 'error') setStatus({ kind: 'idle' })
  }

  function jump(target: number) {
    if (target <= step) setStep(target)
  }

  function startOver() {
    setState(initialState)
    setStatus({ kind: 'idle' })
    setStep(1)
  }

  async function generate() {
    setStatus({ kind: 'loading' })
    try {
      const blob = await initProject({
        projectName: state.projectName,
        frontend: state.frontend,
        backend: state.backend,
        pipeline: state.pipeline,
        includeDocker: state.includeDocker,
      })
      downloadBlob(blob, `${state.projectName}.zip`)
      setStatus({ kind: 'success' })
    } catch (err) {
      setStatus({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Generation failed.',
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
          <a className="text-link" href="https://github.com/filipjoksovic/scaffy" rel="noreferrer" target="_blank">
            GitHub
          </a>
        </div>
      </nav>

      <section aria-labelledby="wizard-title" className="wizard-band">
        <header className="wizard-header">
          <Eyebrow>Project initializer</Eyebrow>
          <h1 id="wizard-title">Scaffold a new project with a working pipeline.</h1>
          <p>
            Pick a stack, a CI provider, and a project name. Scaffy returns a ZIP with the
            scaffold, a Dockerfile, and the pipeline configuration ready to push.
          </p>
        </header>

        <StepIndicator current={step} onJump={jump} steps={STEPS} />

        <div className="wizard-panel">
          {step === 1 && <StackStep state={state} update={update} />}
          {step === 2 && <PipelineStep state={state} update={update} />}
          {step === 3 && (
            <ConfigStep error={projectNameError} state={state} update={update} />
          )}
          {step === 4 && <ReviewStep onGenerate={generate} state={state} status={status} />}
        </div>

        {step === 4 && status.kind === 'success' && (
          <div className="wizard-result">
            <StateRow
              detail={`${state.projectName}.zip is downloading. Unzip and follow the README.`}
              icon="✓"
              label="Project generated"
              tone="success"
            />
            <button className="text-link" onClick={startOver} type="button">
              Start over
            </button>
          </div>
        )}

        {status.kind === 'error' && (
          <div className="wizard-result">
            <StateRow detail={status.message} icon="!" label="Generation failed" tone="error" />
          </div>
        )}

        <div className="wizard-footer">
          <Button
            disabled={step === 1}
            onClick={back}
            variant="secondary"
          >
            Back
          </Button>
          {step < TOTAL_STEPS ? (
            <Button disabled={!canAdvance} onClick={next}>
              Continue
            </Button>
          ) : (
            <Button
              disabled={status.kind === 'loading'}
              onClick={generate}
              variant="download"
            >
              {status.kind === 'loading' ? 'Generating…' : 'Generate project'}
            </Button>
          )}
        </div>
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

function StackStep({ state, update }: StepProps) {
  return (
    <div className="wizard-step">
      <div className="wizard-step__group">
        <Eyebrow>Frontend</Eyebrow>
        <div className="choice-grid" role="radiogroup" aria-label="Frontend framework">
          {FRONTENDS.map((option) => (
            <ChoiceCard
              available={option.available}
              description={option.description}
              key={option.id}
              name={option.name}
              onSelect={() => update('frontend', option.id)}
              selected={state.frontend === option.id}
            />
          ))}
        </div>
      </div>

      <div className="wizard-step__group">
        <Eyebrow>Backend</Eyebrow>
        <div className="choice-grid" role="radiogroup" aria-label="Backend framework">
          {BACKENDS.map((option) => (
            <ChoiceCard
              available={option.available}
              description={option.description}
              key={option.id}
              name={option.name}
              onSelect={() => update('backend', option.id)}
              selected={state.backend === option.id}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function PipelineStep({ state, update }: StepProps) {
  return (
    <div className="wizard-step">
      <div className="wizard-step__group">
        <Eyebrow>CI / CD pipeline</Eyebrow>
        <div className="choice-grid choice-grid--two" role="radiogroup" aria-label="Pipeline provider">
          {PIPELINES.map((option) => (
            <ChoiceCard
              available={option.available}
              description={option.description}
              key={option.id}
              name={option.name}
              onSelect={() => update('pipeline', option.id)}
              selected={state.pipeline === option.id}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

type ConfigStepProps = StepProps & { error: string | null }

function ConfigStep({ error, state, update }: ConfigStepProps) {
  return (
    <div className="wizard-step">
      <div className="wizard-step__group">
        <Eyebrow>Project name</Eyebrow>
        <Card as="form" className="config-form" onSubmit={(event) => event.preventDefault()}>
          <label htmlFor="project-name">Project identifier</label>
          <TextInput
            aria-describedby="project-name-help"
            aria-invalid={error !== null}
            autoComplete="off"
            id="project-name"
            onChange={(event) => update('projectName', event.target.value)}
            placeholder="my-app"
            value={state.projectName}
          />
          <p
            className={error ? 'config-form__hint config-form__hint--error' : 'config-form__hint'}
            id="project-name-help"
          >
            {error ??
              'Lowercase letters, digits, hyphens. 2–64 chars. Must start with a letter and end with a letter or digit.'}
          </p>
        </Card>
      </div>

      <div className="wizard-step__group">
        <Eyebrow>Docker</Eyebrow>
        <Card as="div" className="config-form config-form--toggle">
          <div className="config-form__toggle-row">
            <div>
              <label htmlFor="include-docker">Include Docker support</label>
              <p className="config-form__hint">Adds a Dockerfile for each service and a docker-compose.yml to your project.</p>
            </div>
            <input
              checked={state.includeDocker}
              id="include-docker"
              onChange={(event) => update('includeDocker', event.target.checked)}
              type="checkbox"
            />
          </div>
        </Card>
      </div>
    </div>
  )
}

type ReviewStepProps = {
  onGenerate: () => void
  state: WizardState
  status: GenerationStatus
}

function ReviewStep({ state, status }: ReviewStepProps) {
  const frontend = findOption(FRONTENDS, state.frontend)
  const backend = findOption(BACKENDS, state.backend)
  const pipeline = findOption(PIPELINES, state.pipeline)

  return (
    <div className="wizard-step">
      <div className="wizard-step__group">
        <Eyebrow>Review</Eyebrow>
        <Card className="review-card">
          <dl className="review-list">
            <div>
              <dt>Project name</dt>
              <dd>
                <code>{state.projectName}</code>
              </dd>
            </div>
            <div>
              <dt>Frontend</dt>
              <dd>{frontend?.name ?? '—'}</dd>
            </div>
            <div>
              <dt>Backend</dt>
              <dd>{backend?.name ?? '—'}</dd>
            </div>
            <div>
              <dt>Pipeline</dt>
              <dd>{pipeline?.name ?? '—'}</dd>
            </div>
            <div>
              <dt>Docker</dt>
              <dd>{state.includeDocker ? 'Yes' : 'No'}</dd>
            </div>
          </dl>
        </Card>
        {status.kind === 'loading' && (
          <StateRow detail="Calling /api/init…" label="Generating ZIP" tone="loading" />
        )}
      </div>
    </div>
  )
}
