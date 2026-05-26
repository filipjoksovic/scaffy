import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { oauthLoginUrl } from '../api/auth'
import {
  analyzeRepository,
  connectRepository,
  disconnectRepository,
  listGitHubRepositories,
  listRepositoryConnections,
  type GitHubRepository,
  type RepositoryAnalysis,
  type RepositoryConnection,
} from '../api/repositories'
import { AppFrame, Badge, Button, Card, Eyebrow, StateRow, TextInput } from '../components'
import { useAuth } from '../lib/auth'
import {
  collectFindings,
  countIssues,
  formatDimension,
  formatProvider,
  formatScore,
  statusBadgeClassName,
} from '../lib/analyzer'

type GitHubAccessState = 'connected' | 'needs-reconnect' | 'unknown'

export function Dashboard() {
  const { user, loading } = useAuth()
  const [repository, setRepository] = useState('')
  const [connections, setConnections] = useState<RepositoryConnection[]>([])
  const [githubRepositories, setGitHubRepositories] = useState<GitHubRepository[]>([])
  const [connectionsLoading, setConnectionsLoading] = useState(false)
  const [githubLoading, setGitHubLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [githubFilter, setGithubFilter] = useState('')
  const [githubAccess, setGithubAccess] = useState<GitHubAccessState>('unknown')
  const [analysis, setAnalysis] = useState<RepositoryAnalysis | null>(null)
  const [analyzingId, setAnalyzingId] = useState<string | null>(null)
  const [analysisError, setAnalysisError] = useState<string | null>(null)

  useEffect(() => {
    if (!user) {
      setConnections([])
      return
    }

    let mounted = true
    setConnectionsLoading(true)
    setError(null)
    listRepositoryConnections()
      .then((items) => {
        if (mounted) {
          setConnections(items)
        }
      })
      .catch((err: unknown) => {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Could not load connected projects.')
        }
      })
      .finally(() => {
        if (mounted) {
          setConnectionsLoading(false)
        }
      })

    return () => {
      mounted = false
    }
  }, [user])

  const connectedCount = connections.length
  const needsGitHubReconnect = error?.toLowerCase().includes('reconnect with github') ?? false

  const filteredConnections = useMemo(() => {
    if (!filter.trim()) return connections
    const query = filter.trim().toLowerCase()
    return connections.filter((connection) =>
      `${connection.owner}/${connection.name}`.toLowerCase().includes(query),
    )
  }, [connections, filter])

  const filteredGithubRepositories = useMemo(() => {
    if (!githubFilter.trim()) return githubRepositories
    const query = githubFilter.trim().toLowerCase()
    return githubRepositories.filter((repo) => repo.fullName.toLowerCase().includes(query))
  }, [githubFilter, githubRepositories])

  const accessState: GitHubAccessState = needsGitHubReconnect ? 'needs-reconnect' : githubAccess
  const githubAccessLabel =
    accessState === 'needs-reconnect' ? 'Reconnect needed' : accessState === 'connected' ? 'Connected' : 'Not checked'
  const githubAccessDot =
    accessState === 'needs-reconnect' ? 'error' : accessState === 'connected' ? 'success' : 'warn'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!repository.trim()) {
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      const connection = await connectRepository(repository)
      setConnections((current) => [connection, ...current.filter((item) => item.id !== connection.id)])
      setRepository('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not connect repository.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisconnect(id: string) {
    setError(null)
    setAnalysisError(null)
    try {
      await disconnectRepository(id)
      setConnections((current) => current.filter((item) => item.id !== id))
      setAnalysis((current) => (current?.repositoryId === id ? null : current))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove repository.')
    }
  }

  async function handleAnalyzeRepository(connection: RepositoryConnection) {
    setAnalyzingId(connection.id)
    setAnalysisError(null)
    setAnalysis(null)
    try {
      setAnalysis(await analyzeRepository(connection.id))
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : 'Could not analyze repository.')
    } finally {
      setAnalyzingId(null)
    }
  }

  async function handleFetchGitHubRepositories() {
    setGitHubLoading(true)
    setError(null)
    try {
      setGitHubRepositories(await listGitHubRepositories())
      setGithubAccess('connected')
    } catch (err) {
      setGithubAccess('needs-reconnect')
      setError(err instanceof Error ? err.message : 'Could not fetch GitHub repositories.')
    } finally {
      setGitHubLoading(false)
    }
  }

  async function handleConnectGitHubRepository(repo: GitHubRepository) {
    setSubmitting(true)
    setError(null)
    try {
      const connection = await connectRepository(repo.fullName)
      setConnections((current) => [connection, ...current.filter((item) => item.id !== connection.id)])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not connect repository.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <AppFrame>
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Checking session</h2>
            <p>Verifying the current browser session before loading the workspace.</p>
          </Card>
        </section>
      </AppFrame>
    )
  }

  if (!user) {
    return (
      <AppFrame>
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Sign in to view your projects</h2>
            <p>Connect a GitHub account to discover repositories and queue them for analysis.</p>
            <div className="dashboard-signin__actions">
              <a className="button button--primary" href={oauthLoginUrl.github}>
                Continue with GitHub
              </a>
              <a className="button button--secondary" href={oauthLoginUrl.google}>
                Continue with Google
              </a>
            </div>
          </Card>
        </section>
      </AppFrame>
    )
  }

  return (
    <AppFrame>
      <section className="dashboard-band" aria-labelledby="dashboard-title">
        <header className="dashboard-header">
          <div className="dashboard-header__copy">
            <Eyebrow>Workspace</Eyebrow>
            <h2 id="dashboard-title">Connected projects</h2>
            <p>
              Manage the GitHub repositories Scaffy can analyze. Connect new projects, audit access,
              and queue them for the pipeline grader.
            </p>
          </div>
          <div className="dashboard-header__actions">
            <Button
              disabled={githubLoading}
              onClick={() => void handleFetchGitHubRepositories()}
              variant="secondary"
            >
              {githubLoading ? 'Syncing' : 'Sync GitHub'}
            </Button>
            <a className="button button--primary" href="#quick-connect">
              Connect repository
            </a>
          </div>
        </header>

        <div className="dashboard-summary" aria-label="Repository workspace status">
          <div className="dashboard-summary__item">
            <span>Connected</span>
            <strong>{connectedCount}</strong>
          </div>
          <div className="dashboard-summary__item">
            <span>GitHub access</span>
            <strong>
              <span aria-hidden="true" className={`dot dot--${githubAccessDot}`} />
              {githubAccessLabel}
            </strong>
          </div>
          <div className="dashboard-summary__item">
            <span>Fetched</span>
            <strong>{githubRepositories.length}</strong>
          </div>
          <div className="dashboard-summary__item dashboard-summary__item--wide">
            <span>Next step</span>
            <strong>{connectedCount === 0 ? 'Connect a repository' : 'Analysis will be enabled later'}</strong>
          </div>
        </div>

        <div className="dashboard-grid">
          <div className="dashboard-main">
            <Card as="section" className="panel">
              <div className="panel__header">
                <div className="panel__heading">
                  <Eyebrow>Repositories</Eyebrow>
                  <h3>
                    {connectedCount} connected {connectedCount === 1 ? 'project' : 'projects'}
                  </h3>
                </div>
                <div className="panel__actions">
                  <SearchInput
                    onChange={setFilter}
                    placeholder="Filter by owner or name"
                    value={filter}
                  />
                </div>
              </div>

              {connectionsLoading ? (
                <div className="panel__body">
                  <StateRow
                    detail="Loading repositories from /api/repositories."
                    label="Loading connected projects"
                    tone="loading"
                  />
                </div>
              ) : connections.length === 0 ? (
                <div className="empty-state">
                  <h4>No projects connected</h4>
                  <p>
                    Paste a repository URL on the right or sync your GitHub account to add the first
                    one.
                  </p>
                </div>
              ) : filteredConnections.length === 0 ? (
                <div className="empty-state">
                  <h4>No matches</h4>
                  <p>No connected project matches “{filter}”.</p>
                </div>
              ) : (
                <div className="panel__body panel__body--flush">
                  <table className="repo-table">
                    <thead>
                      <tr>
                        <th scope="col">Project</th>
                        <th scope="col">Provider</th>
                        <th scope="col">Connected</th>
                        <th className="repo-table__actions-heading" scope="col">
                          Actions
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredConnections.map((connection) => (
                        <tr key={connection.id}>
                          <td>
                            <div className="repo-cell">
                              <span aria-hidden="true" className="repo-cell__avatar">
                                {connection.owner.charAt(0).toUpperCase()}
                              </span>
                              <div className="repo-cell__text">
                                <span className="repo-cell__name">
                                  {connection.owner}/{connection.name}
                                </span>
                                <span className="repo-cell__url">
                                  <a href={connection.url} rel="noreferrer" target="_blank">
                                    {connection.url}
                                  </a>
                                </span>
                              </div>
                            </div>
                          </td>
                          <td>
                            <Badge>{connection.provider}</Badge>
                          </td>
                          <td>
                            <span className="row-status">
                              <span aria-hidden="true" className="dot dot--success" />
                              {formatRelative(connection.connectedAt)}
                            </span>
                          </td>
                          <td>
                            <div className="row-actions">
                              <Button
                                className="button--small"
                                disabled={analyzingId === connection.id}
                                onClick={() => void handleAnalyzeRepository(connection)}
                                variant="secondary"
                              >
                                {analyzingId === connection.id ? 'Analyzing' : 'Analyze'}
                              </Button>
                              <a
                                aria-label={`Open ${connection.owner}/${connection.name} on GitHub`}
                                className="icon-button"
                                href={connection.url}
                                rel="noreferrer"
                                target="_blank"
                                title="Open on GitHub"
                              >
                                <IconExternal />
                              </a>
                              <button
                                aria-label={`Disconnect ${connection.owner}/${connection.name}`}
                                className="icon-button icon-button--danger"
                                onClick={() => void handleDisconnect(connection.id)}
                                title="Disconnect"
                                type="button"
                              >
                                <IconTrash />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            <RepositoryAnalysisPanel analysis={analysis} error={analysisError} loading={analyzingId !== null} />
          </div>

          <aside className="dashboard-rail" aria-label="Connect repository">
            <Card as="section" className="panel" id="quick-connect">
              <div className="panel__header">
                <div className="panel__heading">
                  <Eyebrow>Quick connect</Eyebrow>
                  <h3>Add by URL</h3>
                  <p>Paste a GitHub repository to register it without syncing the account.</p>
                </div>
              </div>
              <div className="panel__body">
                <form className="quick-connect" onSubmit={handleSubmit}>
                  <label htmlFor="repository">Repository</label>
                  <div className="input-row">
                    <TextInput
                      id="repository"
                      onChange={(event) => setRepository(event.target.value)}
                      placeholder="owner/repo"
                      value={repository}
                    />
                    <Button disabled={submitting} type="submit">
                      {submitting ? 'Connecting' : 'Connect'}
                    </Button>
                  </div>
                  <span className="quick-connect__hint">
                    owner/repo · https://github.com/owner/repo
                  </span>
                  {error && (
                    <div>
                      <p className="form-error">{error}</p>
                      {needsGitHubReconnect && (
                        <div className="form-error__actions">
                          <a className="button button--secondary button--small" href={oauthLoginUrl.github}>
                            Reconnect GitHub
                          </a>
                        </div>
                      )}
                    </div>
                  )}
                </form>
              </div>
            </Card>

            <Card as="section" className="panel">
              <div className="panel__header">
                <div className="panel__heading">
                  <Eyebrow>From GitHub</Eyebrow>
                  <h3>Your repositories</h3>
                  <p>
                    {githubRepositories.length === 0
                      ? 'Sync to load repositories from the connected GitHub account.'
                      : `${githubRepositories.length} repositories available`}
                  </p>
                </div>
                <div className="panel__actions">
                  <Button
                    className="button--small"
                    disabled={githubLoading}
                    onClick={() => void handleFetchGitHubRepositories()}
                    variant="secondary"
                  >
                    {githubLoading ? 'Syncing' : githubRepositories.length === 0 ? 'Sync' : 'Refresh'}
                  </Button>
                </div>
              </div>

              {githubRepositories.length > 0 && (
                <div className="panel__body panel__body--search">
                  <SearchInput
                    onChange={setGithubFilter}
                    placeholder="Filter repositories"
                    value={githubFilter}
                  />
                </div>
              )}

              <div className={githubRepositories.length > 0 ? 'panel__body panel__body--compact' : 'panel__body'}>
                {githubLoading ? (
                  <StateRow
                    detail="Calling /api/repositories/github."
                    label="Syncing GitHub repositories"
                    tone="loading"
                  />
                ) : githubRepositories.length === 0 ? (
                  <div className="empty-state empty-state--compact">
                    <p>
                      Press <strong>Sync</strong> to fetch repositories from your GitHub account.
                    </p>
                  </div>
                ) : filteredGithubRepositories.length === 0 ? (
                  <div className="empty-state empty-state--compact">
                    <p>No repositories match “{githubFilter}”.</p>
                  </div>
                ) : (
                  <ul className="gh-list">
                    {filteredGithubRepositories.map((repo) => {
                      const connected = connections.some(
                        (connection) =>
                          `${connection.owner}/${connection.name}` === repo.fullName.toLowerCase(),
                      )
                      return (
                        <li className="gh-list__item" key={repo.fullName}>
                          <div className="gh-list__info">
                            <span className="gh-list__name">{repo.fullName}</span>
                            <span className="gh-list__meta">
                              <span aria-hidden="true" className="dot" />
                              {repo.privateRepository ? 'Private' : 'Public'}
                            </span>
                          </div>
                          <Button
                            className="button--small"
                            disabled={connected || submitting}
                            onClick={() => void handleConnectGitHubRepository(repo)}
                            variant={connected ? 'secondary' : 'primary'}
                          >
                            {connected ? 'Connected' : 'Connect'}
                          </Button>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </div>
            </Card>
          </aside>
        </div>
      </section>
    </AppFrame>
  )
}

type RepositoryAnalysisPanelProps = Readonly<{
  analysis: RepositoryAnalysis | null
  error: string | null
  loading: boolean
}>

function RepositoryAnalysisPanel({ analysis, error, loading }: RepositoryAnalysisPanelProps) {
  if (loading) {
    return (
      <Card as="section" className="analysis-panel">
        <StateRow
          detail="Finding GitHub Actions workflows and running the Scaffy capability analyzer."
          label="Analyzing repository"
          tone="loading"
        />
      </Card>
    )
  }

  if (error) {
    return (
      <Card as="section" className="analysis-panel">
        <StateRow detail={error} icon="!" label="Repository analysis failed" tone="error" />
      </Card>
    )
  }

  if (!analysis) {
    return (
      <Card as="section" className="analysis-panel analysis-panel--empty">
        <Eyebrow>Analysis</Eyebrow>
        <h3>No repository analysis yet</h3>
        <p>Select Analyze on a connected project to scrape its GitHub Actions workflow and score it.</p>
      </Card>
    )
  }

  const issueCount = analysis.analysis.dimensions.reduce(
    (total, dimension) => total + countIssues(dimension),
    0,
  )

  return (
    <Card as="section" className="analysis-panel">
      <div className="analysis-panel__header">
        <div>
          <Eyebrow>Analysis result</Eyebrow>
          <h3>{analysis.repository}</h3>
          <p>
            {formatProvider(analysis.analysis.provider)} · <code>{analysis.workflowPath}</code>
          </p>
        </div>
        <div className="analysis-score">
          <strong>{formatScore(analysis.analysis.overallScore)}</strong>
          <span>Level {analysis.analysis.overallLevel}</span>
        </div>
      </div>

      <div className="analysis-meta">
        <Badge>{formatProvider(analysis.analysis.provider)}</Badge>
        <Badge className={statusBadgeClassName(analysis.analysis.overallStatus)}>
          {analysis.analysis.overallStatus}
        </Badge>
        <span>{issueCount} open {issueCount === 1 ? 'issue' : 'issues'}</span>
      </div>

      <div className="analysis-dimensions">
        {analysis.analysis.dimensions.map((dimension) => {
          const positives = collectFindings(dimension, 'POSITIVE')
          const smells = collectFindings(dimension, 'SMELL')
          const missing = collectFindings(dimension, 'MISSING')
          return (
            <div className="analysis-dimension" key={dimension.dimension}>
              <div>
                <strong>{formatDimension(dimension.dimension)}</strong>
                <span>{dimension.status === 'not_evaluated' ? 'Not evaluated' : `Level ${dimension.level}`}</span>
              </div>
              <div className="analysis-dimension__score">
                {dimension.status === 'not_evaluated' ? '—' : formatScore(dimension.score)}
              </div>
              <div className="analysis-dimension__findings">
                <span>{positives.length} positive</span>
                <span>{smells.length} smells</span>
                <span>{missing.length} missing</span>
              </div>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

type SearchInputProps = Readonly<{
  onChange: (value: string) => void
  placeholder: string
  value: string
}>

function SearchInput({ onChange, placeholder, value }: SearchInputProps) {
  return (
    <label className="search-input">
      <IconSearch />
      <input
        aria-label={placeholder}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type="search"
        value={value}
      />
    </label>
  )
}

function IconSearch() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16">
      <circle cx="7" cy="7" r="5" />
      <path d="m11 11 3.5 3.5" strokeLinecap="round" />
    </svg>
  )
}

function IconExternal() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16">
      <path d="M9 3h4v4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M13 3 7.5 8.5" strokeLinecap="round" />
      <path
        d="M12.5 9.5V12a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 12V5A1.5 1.5 0 0 1 4 3.5h2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function IconTrash() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16">
      <path d="M3 4.5h10" strokeLinecap="round" />
      <path d="M6 4.5V3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1.5" strokeLinecap="round" />
      <path
        d="M4.5 4.5 5 13a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1l.5-8.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function formatRelative(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const diffMs = date.getTime() - Date.now()
  const diffSec = Math.round(diffMs / 1000)
  const abs = Math.abs(diffSec)

  if (abs < 60) return 'just now'
  const minutes = Math.round(diffSec / 60)
  if (Math.abs(minutes) < 60) return formatChunk(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return formatChunk(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 30) return formatChunk(days, 'day')
  const months = Math.round(days / 30)
  if (Math.abs(months) < 12) return formatChunk(months, 'month')
  const years = Math.round(days / 365)
  return formatChunk(years, 'year')
}

function formatChunk(value: number, unit: string): string {
  const n = Math.abs(value)
  const plural = n === 1 ? unit : `${unit}s`
  return value < 0 ? `${n} ${plural} ago` : `in ${n} ${plural}`
}
