import { useState } from 'react'
import * as Popover from '@radix-ui/react-popover'
import { Link, useNavigate } from 'react-router-dom'
import { useWorkspace } from '../lib/workspace'
import { createWorkspace } from '../api/workspaces'

export function WorkspaceSwitcher() {
  const { workspaces, activeWorkspace, loading, selectWorkspace, refresh } = useWorkspace()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (loading && workspaces.length === 0) {
    return <span className="auth-status">Loading workspaces</span>
  }
  if (workspaces.length === 0) {
    return null
  }

  const label = activeWorkspace?.name ?? 'Select workspace'

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Enter a workspace name.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const workspace = await createWorkspace(trimmed)
      await refresh()
      selectWorkspace(workspace.id)
      setName('')
      setCreating(false)
      setOpen(false)
      navigate('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create workspace.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger asChild>
        <button aria-label="Switch workspace" className="workspace-trigger" type="button">
          <span className="workspace-trigger__label">{label}</span>
          <svg aria-hidden="true" viewBox="0 0 16 16" width="12" height="12">
            <path
              d="M3.5 6l4.5 4 4.5-4"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="end" className="account-menu" collisionPadding={12} sideOffset={8}>
          <div className="account-menu__head">
            <strong>Workspaces</strong>
          </div>
          <div className="account-menu__items">
            {workspaces.map((workspace) => (
              <button
                className={
                  workspace.id === activeWorkspace?.id
                    ? 'account-menu__item account-menu__item--active'
                    : 'account-menu__item'
                }
                key={workspace.id}
                onClick={() => {
                  selectWorkspace(workspace.id)
                  setOpen(false)
                }}
                type="button"
              >
                <span>{workspace.name}</span>
                {workspace.id === activeWorkspace?.id && <span aria-hidden="true">✓</span>}
              </button>
            ))}
          </div>
          <div className="account-menu__items">
            <Popover.Close asChild>
              <Link className="account-menu__item" to="/workspace">
                <span>Workspace settings</span>
              </Link>
            </Popover.Close>
            {creating ? (
              <form className="workspace-create" onSubmit={handleCreate}>
                <input
                  autoFocus
                  className="text-input"
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Workspace name"
                  value={name}
                />
                {error && <p className="form-error">{error}</p>}
                <div className="workspace-create__actions">
                  <button className="button button--primary" disabled={busy} type="submit">
                    {busy ? 'Creating…' : 'Create'}
                  </button>
                  <button
                    className="button"
                    onClick={() => {
                      setCreating(false)
                      setError(null)
                    }}
                    type="button"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            ) : (
              <button
                className="account-menu__item"
                onClick={() => setCreating(true)}
                type="button"
              >
                <span>+ New workspace</span>
              </button>
            )}
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
