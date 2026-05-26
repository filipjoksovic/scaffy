import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { oauthLoginUrl } from '../api/auth'
import {
  connectRepository,
  disconnectRepository,
  listGitHubRepositories,
  listRepositoryConnections,
  type GitHubRepository,
  type RepositoryConnection,
} from '../api/repositories'
import { Badge, Button, Card, TextInput } from '../components'
import { useAuth } from '../lib/auth'

export function Dashboard() {
  const { user, loading } = useAuth()
  const [repository, setRepository] = useState('')
  const [connections, setConnections] = useState<RepositoryConnection[]>([])
  const [githubRepositories, setGitHubRepositories] = useState<GitHubRepository[]>([])
  const [connectionsLoading, setConnectionsLoading] = useState(false)
  const [githubLoading, setGitHubLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

  const connectedCount = useMemo(() => connections.length, [connections])
  const needsGitHubReconnect = error?.toLowerCase().includes('reconnect with github') ?? false

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
    try {
      await disconnectRepository(id)
      setConnections((current) => current.filter((item) => item.id !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove repository.')
    }
  }

  async function handleFetchGitHubRepositories() {
    setGitHubLoading(true)
    setError(null)
    try {
      setGitHubRepositories(await listGitHubRepositories())
    } catch (err) {
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
      <section className="dashboard-band">
        <Card as="section">
          <Badge>Dashboard</Badge>
          <h1>Loading session</h1>
          <p>Checking the current browser session.</p>
        </Card>
      </section>
    )
  }

  if (!user) {
    return (
      <section className="dashboard-band">
        <Card as="section">
          <Badge>Dashboard</Badge>
          <h1>Connect a GitHub project</h1>
          <p>Sign in with GitHub before connecting projects to this workspace.</p>
          <div className="dashboard-actions">
            <a className="button button--primary" href={oauthLoginUrl.github}>
              GitHub login
            </a>
            <a className="button button--secondary" href={oauthLoginUrl.google}>
              Google login
            </a>
          </div>
        </Card>
      </section>
    )
  }

  return (
    <section className="dashboard-band">
      <div className="dashboard-heading">
        <div>
          <Badge>Dashboard</Badge>
          <h1>Connected projects</h1>
          <p>Connect GitHub repositories now; pipeline discovery and scoring will use these entries later.</p>
        </div>
        <div className="dashboard-metric" aria-label={`${connectedCount} connected projects`}>
          <strong>{connectedCount}</strong>
          <span>{connectedCount === 1 ? 'project' : 'projects'}</span>
        </div>
      </div>

      <form className="card repository-form" onSubmit={handleSubmit}>
        <label htmlFor="repository">GitHub repository</label>
        <div className="input-row">
          <TextInput
            id="repository"
            onChange={(event) => setRepository(event.target.value)}
            placeholder="owner/repo or https://github.com/owner/repo"
            value={repository}
          />
          <Button disabled={submitting} type="submit">
            {submitting ? 'Connecting' : 'Connect'}
          </Button>
        </div>
        {error && (
          <div className="form-error-block">
            <p className="form-error">{error}</p>
            {needsGitHubReconnect && (
              <a className="button button--secondary" href={oauthLoginUrl.github}>
                Reconnect GitHub
              </a>
            )}
          </div>
        )}
      </form>

      <Card as="section" className="github-fetch-panel">
        <div>
          <h2>Fetch from GitHub</h2>
          <p>Load repositories from your GitHub account and connect one without pasting its URL.</p>
        </div>
        <Button disabled={githubLoading} onClick={() => void handleFetchGitHubRepositories()} variant="secondary">
          {githubLoading ? 'Fetching' : 'Fetch GitHub projects'}
        </Button>
      </Card>

      {githubRepositories.length > 0 && (
        <div className="repository-list">
          {githubRepositories.map((repo) => {
            const connected = connections.some(
              (connection) => `${connection.owner}/${connection.name}` === repo.fullName.toLowerCase(),
            )
            return (
              <Card as="article" className="repository-card" key={repo.fullName}>
                <div>
                  <Badge>{repo.privateRepository ? 'private' : 'public'}</Badge>
                  <h2>{repo.fullName}</h2>
                  <a href={repo.url} rel="noreferrer" target="_blank">
                    {repo.url}
                  </a>
                </div>
                <Button
                  disabled={connected || submitting}
                  onClick={() => void handleConnectGitHubRepository(repo)}
                  variant={connected ? 'secondary' : 'primary'}
                >
                  {connected ? 'Connected' : 'Connect'}
                </Button>
              </Card>
            )
          })}
        </div>
      )}

      <div className="repository-list" aria-live="polite">
        {connectionsLoading ? (
          <Card as="section">
            <p>Loading connected projects.</p>
          </Card>
        ) : connections.length === 0 ? (
          <Card as="section">
            <h2>No connected projects</h2>
            <p>Add a GitHub repository to make it available for the next analysis milestone.</p>
          </Card>
        ) : (
          connections.map((connection) => (
            <Card as="article" className="repository-card" key={connection.id}>
              <div>
                <Badge>{connection.provider}</Badge>
                <h2>{connection.owner}/{connection.name}</h2>
                <a href={connection.url} rel="noreferrer" target="_blank">
                  {connection.url}
                </a>
              </div>
              <Button
                aria-label={`Disconnect ${connection.owner}/${connection.name}`}
                onClick={() => void handleDisconnect(connection.id)}
                variant="secondary"
              >
                Disconnect
              </Button>
            </Card>
          ))
        )}
      </div>
    </section>
  )
}
