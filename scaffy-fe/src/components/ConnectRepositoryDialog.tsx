import { useEffect, useMemo, useState } from 'react'
import type { SyntheticEvent } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { Search, X } from 'lucide-react'
import { connectProviderUrl, listConnections, type ProviderConnection } from '../api/auth'
import {
  connectRepository,
  listGitHubRepositories,
  listGitLabRepositories,
  type GitHubRepository,
  type RepositoryConnection,
} from '../api/repositories'
import {
  listWorkspaceGitlabInstances,
  type WorkspaceGitlabInstance,
} from '../api/workspaces'
import { useWorkspace } from '../lib/workspace'
import { Button } from './Button'
import { Eyebrow } from './Eyebrow'
import { ProviderLogo } from './ProviderLogo'
import { StateRow } from './StateRow'
import { TextInput } from './TextInput'

type ProviderTab = {
  key: string
  label: string
  provider: 'github' | 'gitlab'
  instance: string
  registrationId: string
  connected: boolean
}

type ConnectRepositoryDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  existingConnections: RepositoryConnection[]
  onConnected: (connection: RepositoryConnection) => void
  initialProviderKey?: string | null
}

export function ConnectRepositoryDialog({
  open,
  onOpenChange,
  existingConnections,
  onConnected,
  initialProviderKey,
}: Readonly<ConnectRepositoryDialogProps>) {
  const { activeWorkspace } = useWorkspace()
  const [connections, setConnections] = useState<ProviderConnection[]>([])
  const [instances, setInstances] = useState<WorkspaceGitlabInstance[]>([])
  const [activeKey, setActiveKey] = useState('github')
  const [metaLoading, setMetaLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    if (initialProviderKey) {
      // Sync prop -> active tab on open; the loading flag below is set synchronously by design.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setActiveKey(initialProviderKey)
    }
    let mounted = true
    setMetaLoading(true)
    Promise.all([
      listConnections(),
      activeWorkspace ? listWorkspaceGitlabInstances(activeWorkspace.id) : Promise.resolve([]),
    ])
      .then(([conns, wsInstances]) => {
        if (!mounted) return
        setConnections(conns)
        setInstances(wsInstances)
      })
      .catch(() => {
        // Non-fatal: tabs still render their connect CTA.
      })
      .finally(() => {
        if (mounted) setMetaLoading(false)
      })
    return () => {
      mounted = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, activeWorkspace?.id])

  const tabs: ProviderTab[] = useMemo(() => {
    const githubConnected = connections.some((c) => c.provider === 'github')
    const gitlabComConnected = connections.some(
      (c) => c.provider === 'gitlab' && (c.instance === 'gitlab.com' || c.instance === ''),
    )
    const base: ProviderTab[] = [
      {
        key: 'github',
        label: 'GitHub',
        provider: 'github',
        instance: '',
        registrationId: 'github',
        connected: githubConnected,
      },
      {
        key: 'gitlab.com',
        label: 'GitLab.com',
        provider: 'gitlab',
        instance: 'gitlab.com',
        registrationId: 'gitlab',
        connected: gitlabComConnected,
      },
    ]
    const instanceTabs: ProviderTab[] = instances.map((instance) => ({
      key: instance.host,
      label: instance.displayName || instance.host,
      provider: 'gitlab',
      instance: instance.host,
      registrationId: instance.registrationId,
      connected:
        instance.connected ||
        connections.some((c) => c.provider === 'gitlab' && c.instance === instance.host),
    }))
    return [...base, ...instanceTabs]
  }, [connections, instances])

  const activeTab = tabs.find((t) => t.key === activeKey) ?? tabs[0]

  return (
    <Dialog.Root onOpenChange={onOpenChange} open={open}>
      <Dialog.Portal>
        <Dialog.Overlay className="repository-dialog__overlay" />
        <Dialog.Content className="repository-dialog">
          <header className="repository-dialog__header">
            <div>
              <Eyebrow>Add project</Eyebrow>
              <Dialog.Title className="repository-dialog__title">Connect a repository</Dialog.Title>
              <Dialog.Description className="repository-dialog__description">
                Choose a source, connect the account once, then pick a repository to add to this
                workspace.
              </Dialog.Description>
            </div>
            <Dialog.Close className="icon-button" aria-label="Close dialog">
              <IconClose />
            </Dialog.Close>
          </header>

          <div className="connect-provider-tabs" role="tablist" aria-label="Repository source">
            {tabs.map((tab) => (
              <button
                aria-selected={tab.key === activeTab?.key}
                className={
                  tab.key === activeTab?.key
                    ? 'connect-provider-tabs__item connect-provider-tabs__item--active'
                    : 'connect-provider-tabs__item'
                }
                key={tab.key}
                onClick={() => setActiveKey(tab.key)}
                role="tab"
                type="button"
              >
                <ProviderLogo monochrome provider={tab.provider} size={14} />
                {tab.label}
                {tab.connected && <span aria-hidden="true" className="connect-provider-tabs__dot" />}
              </button>
            ))}
          </div>

          {activeTab && (
            <ProviderPanel
              existingConnections={existingConnections}
              key={activeTab.key}
              loadingMeta={metaLoading}
              onConnected={onConnected}
              tab={activeTab}
              workspaceId={activeWorkspace?.id ?? null}
            />
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

type ProviderPanelProps = {
  tab: ProviderTab
  workspaceId: string | null
  existingConnections: RepositoryConnection[]
  loadingMeta: boolean
  onConnected: (connection: RepositoryConnection) => void
}

function manualLabels(provider: ProviderTab['provider']) {
  const placeholder = provider === 'github' ? 'owner/repo' : 'group/project'
  const hint =
    provider === 'github'
      ? 'owner/repo or https://github.com/owner/repo'
      : 'group/subgroup/project path on this instance'
  return { placeholder, hint }
}

function ProviderPanel({ tab, workspaceId, existingConnections, loadingMeta, onConnected }: Readonly<ProviderPanelProps>) {
  const [repos, setRepos] = useState<GitHubRepository[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [manual, setManual] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const reconnectNeeded = error?.toLowerCase().includes('connect') ?? false

  const loadRepos = () =>
    tab.provider === 'github'
      ? listGitHubRepositories()
      : listGitLabRepositories(tab.instance)

  const refreshRepos = () => {
    setLoading(true)
    setError(null)
    return loadRepos()
      .then((items) => setRepos(items))
      .catch((err: unknown) =>
        setError(err instanceof Error ? err.message : 'Could not load repositories.'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (!tab.connected) return
    let mounted = true
    // Mount fetch: loading/error flags are set synchronously by design before the async load.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    setError(null)
    loadRepos()
      .then((items) => {
        if (mounted) setRepos(items)
      })
      .catch((err: unknown) => {
        if (mounted) setError(err instanceof Error ? err.message : 'Could not load repositories.')
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab.key, tab.connected])

  const filtered = useMemo(() => {
    if (!filter.trim()) return repos
    const query = filter.trim().toLowerCase()
    return repos.filter((repo) => repo.fullName.toLowerCase().includes(query))
  }, [repos, filter])

  const isConnected = (fullName: string) =>
    existingConnections.some(
      (c) =>
        c.provider === tab.provider &&
        (c.instance ?? '') === tab.instance &&
        `${c.owner}/${c.name}`.toLowerCase() === fullName.toLowerCase(),
    )

  async function add(fullName: string) {
    setSubmitting(true)
    setError(null)
    try {
      const connection = await connectRepository({
        repository: fullName,
        provider: tab.provider,
        instance: tab.instance || undefined,
      })
      onConnected(connection)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not connect repository.')
    } finally {
      setSubmitting(false)
    }
  }

  async function submitManual(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!manual.trim()) return
    await add(manual.trim())
    setManual('')
  }

  if (loadingMeta && !tab.connected) {
    return (
      <div className="repository-dialog__body">
        <StateRow detail="Checking connected accounts." label="Loading" tone="loading" />
      </div>
    )
  }

  if (!tab.connected) {
    return (
      <div className="repository-dialog__body">
        <div className="connect-cta">
          <Eyebrow>Connect {tab.label}</Eyebrow>
          <h4>Link your {tab.label} account</h4>
          <p>
            {tab.provider === 'github'
              ? 'Authorize Scaffy to read your GitHub repositories. You stay signed in with your current account.'
              : `Authorize Scaffy against ${tab.label} to browse and add your projects.`}
          </p>
          <a
            className="button button--primary"
            href={connectProviderUrl(tab.registrationId, { workspaceId, returnTo: '/dashboard' })}
          >
            Connect {tab.label}
          </a>
        </div>
      </div>
    )
  }

  const { placeholder: manualPlaceholder, hint: manualHint } = manualLabels(tab.provider)

  const renderBody = () => {
    if (loading) {
      return (
        <StateRow detail={`Fetching repositories from ${tab.label}.`} label="Loading repositories" tone="loading" />
      )
    }
    if (error && repos.length === 0) {
      return (
        <div className="repository-dialog__empty">
          <h4>Could not load repositories</h4>
          <p>{error}</p>
          {reconnectNeeded && (
            <a
              className="button button--secondary button--small"
              href={connectProviderUrl(tab.registrationId, { workspaceId, returnTo: '/dashboard' })}
            >
              Reconnect {tab.label}
            </a>
          )}
        </div>
      )
    }
    if (filtered.length === 0) {
      return (
        <div className="repository-dialog__empty">
          <h4>No repositories</h4>
          <p>{filter ? `No repository matches “${filter}”.` : `No repositories found on ${tab.label}.`}</p>
        </div>
      )
    }
    return (
      <ul className="repository-picker-list">
        {filtered.map((repo) => {
          const connected = isConnected(repo.fullName)
          return (
            <li className="repository-picker-list__item" key={repo.fullName}>
              <button
                className="repository-picker-list__main"
                disabled={connected || submitting}
                onClick={() => void add(repo.fullName)}
                type="button"
              >
                <span className="repository-picker-list__name">{repo.fullName}</span>
                <span className="repository-picker-list__meta">
                  <span aria-hidden="true" className="dot" />
                  {repo.privateRepository ? 'Private' : 'Public'}
                </span>
              </button>
              <Button
                className="button--small"
                disabled={connected || submitting}
                onClick={() => void add(repo.fullName)}
                variant={connected ? 'secondary' : 'primary'}
              >
                {connected ? 'Added' : 'Add'}
              </Button>
            </li>
          )
        })}
      </ul>
    )
  }

  return (
    <>
      <div className="repository-dialog__toolbar">
        <label className="search-input">
          <IconSearch />
          <input
            aria-label="Search repositories"
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Search repositories"
            type="search"
            value={filter}
          />
        </label>
        <Button className="button--small" disabled={loading} onClick={() => void refreshRepos()} variant="secondary">
          {loading ? 'Refreshing' : 'Refresh'}
        </Button>
      </div>

      <div className="repository-dialog__body">{renderBody()}</div>

      <form className="repository-dialog__manual" onSubmit={submitManual}>
        <div>
          <label htmlFor="manual-repo">Paste a path</label>
          <span>{manualHint}</span>
        </div>
        <div className="repository-dialog__manual-row">
          <TextInput
            id="manual-repo"
            onChange={(event) => setManual(event.target.value)}
            placeholder={manualPlaceholder}
            value={manual}
          />
          <Button disabled={submitting} type="submit">
            {submitting ? 'Adding' : 'Add'}
          </Button>
        </div>
      </form>

      {error && repos.length > 0 && (
        <div className="repository-dialog__error">
          <p className="form-error">{error}</p>
        </div>
      )}
    </>
  )
}

function IconSearch() {
  return <Search aria-hidden="true" size={16} />
}

function IconClose() {
  return <X aria-hidden="true" size={16} />
}
