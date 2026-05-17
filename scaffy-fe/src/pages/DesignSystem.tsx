import { Link } from 'react-router-dom'
import {
  Button,
  Card,
  CodeBlock,
  Eyebrow,
  StateRow,
  StatusConsole,
  TextInput,
  TimelinePill,
  type TimelineTone,
} from '../components'

const timeline: Array<{ detail: string; label: string; tone: TimelineTone }> = [
  { tone: 'thinking', label: 'Thinking', detail: 'Inspect request scope' },
  { tone: 'grep', label: 'Grepping', detail: 'Find matching services' },
  { tone: 'read', label: 'Reading', detail: 'Open deployment files' },
  { tone: 'edit', label: 'Editing', detail: 'Patch frontend route' },
  { tone: 'done', label: 'Done', detail: 'Build passed' },
]

const serviceRows = [
  { service: 'Frontend', target: 'Vite preview', status: 'Online' },
  { service: 'Backend', target: '/api/health', status: 'Healthy' },
  { service: 'Traefik', target: 'Same-origin route', status: 'Active' },
  { service: 'PostgreSQL', target: 'Primary store', status: 'Ready' },
]

const tokens = [
  ['Canvas', '#f7f7f4'],
  ['Ink', '#26251e'],
  ['Primary', '#f54e00'],
  ['Hairline', '#e6e5e0'],
]

const codeSample = `const health = await fetch('/api/health')
const status = await health.json()

return {
  service: status.service,
  frontend: 'scaffy-fe',
  route: 'same-origin',
}`

export function DesignSystem() {
  return (
    <main className="app-shell">
      <nav className="top-nav" aria-label="Primary">
        <Link className="wordmark" to="/" aria-label="Scaffy home">
          <span className="wordmark-mark" aria-hidden="true" />
          Scaffy
        </Link>
        <div className="nav-links">
          <a href="#workspace">Workspace</a>
          <a href="#language">Design Language</a>
          <a href="#timeline">Agent Timeline</a>
          <a href="#states">States</a>
        </div>
        <div className="nav-actions">
          <Link className="text-link" to="/init">
            Back to initializer
          </Link>
        </div>
        <button className="menu-button" type="button" aria-label="Open menu">
          <span />
          <span />
        </button>
      </nav>

      <section className="workspace-band" id="workspace" aria-labelledby="workspace-title">
        <div className="workspace-copy">
          <Eyebrow>Scaffy design language demo</Eyebrow>
          <h1 id="workspace-title">Practical product UI on a warm technical canvas.</h1>
          <p>
            A compact operating surface for deployment health, API connectivity,
            agent activity, forms, tables, cards, code, and explicit states.
          </p>
          <div className="hero-actions">
            <Button variant="download">Download for macOS</Button>
            <a className="text-link" href="#language">
              Review tokens
            </a>
          </div>
        </div>

        <StatusConsole
          label="scaffy-fe"
          metrics={[
            { value: '99.98%', label: 'Availability' },
            { value: '81 ms', label: 'P95 route time' },
          ]}
          note="Routing and health endpoints are aligned with same-origin API paths."
          noteCode="GET /api/health"
          rows={serviceRows}
          statusLabel="Live"
          title="Health summary"
        />
      </section>

      <section className="section-grid" id="language" aria-labelledby="language-title">
        <div className="section-heading">
          <Eyebrow>Tokens</Eyebrow>
          <h2 id="language-title">Warm surfaces, scarce orange, hairline structure.</h2>
        </div>
        <div className="token-strip" aria-label="Core color tokens">
          {tokens.map(([name, value]) => (
            <Card className="token-swatch" key={name}>
              <span style={{ backgroundColor: value }} aria-hidden="true" />
              <strong>{name}</strong>
              <code>{value}</code>
            </Card>
          ))}
        </div>
      </section>

      <section className="split-band" id="timeline" aria-labelledby="timeline-title">
        <div>
          <Eyebrow>Agent timeline</Eyebrow>
          <h2 id="timeline-title">Pastel pills stay inside product activity.</h2>
          <p>
            The timeline palette marks AI action stages only. It never becomes a
            competing action or semantic color system.
          </p>
        </div>
        <ol className="timeline-list">
          {timeline.map((item) => (
            <li key={item.label}>
              <TimelinePill tone={item.tone}>{item.label}</TimelinePill>
              <span>{item.detail}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className="component-grid" aria-label="Component examples">
        <Card>
          <Eyebrow>Feature card</Eyebrow>
          <h3>Deployment readiness</h3>
          <p>Dense status, explicit labels, and no decorative elevation.</p>
        </Card>
        <Card inverted>
          <Eyebrow>Featured tier</Eyebrow>
          <h3>Team workspace</h3>
          <p>Dark inversion signals emphasis without adding a ribbon.</p>
        </Card>
        <Card as="form" className="form-card">
          <label htmlFor="repo-url">Repository URL</label>
          <div className="input-row">
            <TextInput id="repo-url" placeholder="https://github.com/acme/scaffy" />
            <Button>Analyze</Button>
          </div>
        </Card>
      </section>

      <section className="code-band" aria-labelledby="code-title">
        <div>
          <Eyebrow>Code surface</Eyebrow>
          <h2 id="code-title">JetBrains Mono on every technical block.</h2>
        </div>
        <CodeBlock code={codeSample} />
      </section>

      <section className="states-band" id="states" aria-labelledby="states-title">
        <div>
          <Eyebrow>States</Eyebrow>
          <h2 id="states-title">Loading, empty, error, and success are visible.</h2>
        </div>
        <div className="state-grid">
          <StateRow tone="loading" label="Loading" detail="Checking backend route." />
          <StateRow tone="empty" label="Empty" detail="No deployments queued." />
          <StateRow tone="error" label="Error" detail="API health check failed." icon="!" />
          <StateRow tone="success" label="Success" detail="Preview is reachable." icon="✓" />
        </div>
      </section>

      <footer className="footer">
        <strong>Scaffy</strong>
        <a href="#workspace">Workspace</a>
        <a href="#language">Tokens</a>
        <a href="#timeline">Timeline</a>
        <a href="#states">States</a>
      </footer>
    </main>
  )
}
