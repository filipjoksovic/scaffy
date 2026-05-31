import * as Popover from '@radix-ui/react-popover'
import { Bell } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import {
  acceptInvitation,
  listMyInvitations,
  type WorkspaceInvitation,
} from '../api/workspaces'
import { useWorkspace } from '../lib/workspace'

const POLL_INTERVAL_MS = 60_000

export function NotificationBell() {
  const { refresh: refreshWorkspaces } = useWorkspace()
  const [invitations, setInvitations] = useState<WorkspaceInvitation[]>([])
  const [acceptingToken, setAcceptingToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setInvitations(await listMyInvitations())
    } catch {
      setInvitations([])
    }
  }, [])

  useEffect(() => {
    // Mount + interval fetch via a reused loader; its setState runs after the async resolve.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
    const id = window.setInterval(() => void load(), POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [load])

  const handleAccept = useCallback(
    async (token: string) => {
      setAcceptingToken(token)
      setError(null)
      try {
        await acceptInvitation(token)
        await Promise.all([refreshWorkspaces(), load()])
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not accept invitation.')
      } finally {
        setAcceptingToken(null)
      }
    },
    [load, refreshWorkspaces],
  )

  const count = invitations.length

  return (
    <Popover.Root>
      <Popover.Trigger asChild>
        <button
          aria-label={
            count > 0 ? `Notifications, ${count} pending` : 'Notifications'
          }
          className="notif-trigger"
          type="button"
        >
          <Bell aria-hidden="true" size={18} strokeWidth={1.8} />
          {count > 0 && (
            <span aria-hidden="true" className="notif-trigger__badge">
              {count > 9 ? '9+' : count}
            </span>
          )}
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          className="notif-menu"
          collisionPadding={12}
          sideOffset={8}
        >
          <div className="notif-menu__head">
            <strong>Notifications</strong>
          </div>
          {error && <p className="notif-menu__error">{error}</p>}
          {count === 0 ? (
            <p className="notif-menu__empty">You're all caught up.</p>
          ) : (
            <ul className="notif-list">
              {invitations.map((invitation) => (
                <li className="notif-list__row" key={invitation.id}>
                  <div className="notif-list__text">
                    <strong>{invitation.workspaceName}</strong>
                    <span>Invited to join as {invitation.role}</span>
                  </div>
                  <button
                    className="notif-list__accept"
                    disabled={acceptingToken === invitation.token}
                    onClick={() => void handleAccept(invitation.token)}
                    type="button"
                  >
                    {acceptingToken === invitation.token ? 'Joining…' : 'Accept'}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
