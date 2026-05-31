import { useState } from 'react'
import type { SyntheticEvent } from 'react'
import * as Popover from '@radix-ui/react-popover'
import { Link, useNavigate } from 'react-router-dom'
import { Check, ChevronsUpDown, Plus } from 'lucide-react'
import { useWorkspace } from '../lib/workspace'
import { createWorkspace } from '../api/workspaces'

/** Lives in the workspace sidebar: shows the active workspace and switches/creates workspaces. */
export function WorkspaceSwitcher() {
  const { workspaces, activeWorkspace, selectWorkspace, refresh } = useWorkspace()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const label = activeWorkspace?.name ?? 'Workspace'
  const initial = label.trim().charAt(0).toUpperCase() || 'W'
  const role = activeWorkspace?.role

  async function handleCreate(event: SyntheticEvent) {
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
        <button aria-label="Switch workspace" className="ws-switcher" type="button">
          <span aria-hidden="true" className="ws-switcher__avatar">
            {initial}
          </span>
          <span className="ws-switcher__copy">
            <strong>{label}</strong>
            {role && <span>{role}</span>}
          </span>
          <ChevronsUpDown aria-hidden="true" className="ws-switcher__chev" size={15} />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="start" className="account-menu ws-switcher-menu" collisionPadding={12} sideOffset={6}>
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
                {workspace.id === activeWorkspace?.id && <Check aria-hidden="true" size={15} />}
              </button>
            ))}
          </div>
          <div className="account-menu__items">
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
              <button className="account-menu__item" onClick={() => setCreating(true)} type="button">
                <Plus aria-hidden="true" size={15} />
                <span>New workspace</span>
              </button>
            )}
            <Popover.Close asChild>
              <Link className="account-menu__item" to="/workspace">
                <span>Workspace settings</span>
              </Link>
            </Popover.Close>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
