import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { oauthLoginUrl } from '../api/auth'
import { useAuth } from '../lib/auth'
import { useWorkspace } from '../lib/workspace'
import { Card } from './Card'
import { Eyebrow } from './Eyebrow'
import { TopNav } from './TopNav'

type WorkspaceFrameProps = {
  active: 'projects' | 'members' | 'settings'
  children: ReactNode
}

const NAV_ITEMS = [
  { key: 'projects', label: 'Projects', to: '/dashboard', end: true, icon: IconProjects },
  { key: 'members', label: 'Members', to: '/workspace/members', end: false, icon: IconMembers },
  { key: 'settings', label: 'Settings', to: '/workspace', end: true, icon: IconSettings },
] as const

export function WorkspaceFrame({ children }: WorkspaceFrameProps) {
  const { user, loading } = useAuth()
  const { activeWorkspace } = useWorkspace()

  if (loading) {
    return (
      <main className="app-shell">
        <TopNav />
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Checking session</h2>
            <p>Verifying the current browser session before loading the workspace.</p>
          </Card>
        </section>
      </main>
    )
  }

  if (!user) {
    return (
      <main className="app-shell">
        <TopNav />
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Sign in to open your workspace</h2>
            <p>Connect an account to manage projects, members, and workspace settings.</p>
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
      </main>
    )
  }

  const workspaceName = activeWorkspace?.name ?? 'Workspace'
  const workspaceInitial = workspaceName.trim().charAt(0).toUpperCase() || 'W'
  const role = activeWorkspace?.role

  return (
    <main className="app-shell ws-shell">
      <TopNav />
      <div className="ws-body">
        <aside className="ws-sidebar" aria-label="Workspace navigation">
          <div className="ws-identity">
            <span aria-hidden="true" className="ws-identity__avatar">
              {workspaceInitial}
            </span>
            <div className="ws-identity__copy">
              <strong>{workspaceName}</strong>
              {role && <span>{role}</span>}
            </div>
          </div>
          <nav className="ws-nav" aria-label="Workspace sections">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon
              return (
                <NavLink className="ws-nav__item" end={item.end} key={item.key} to={item.to}>
                  <Icon />
                  <span>{item.label}</span>
                </NavLink>
              )
            })}
          </nav>
        </aside>
        <div className="ws-content">{children}</div>
      </div>
    </main>
  )
}

function IconProjects() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16" width="16" height="16">
      <rect x="2.5" y="2.5" width="4.5" height="4.5" rx="1" />
      <rect x="9" y="2.5" width="4.5" height="4.5" rx="1" />
      <rect x="2.5" y="9" width="4.5" height="4.5" rx="1" />
      <rect x="9" y="9" width="4.5" height="4.5" rx="1" />
    </svg>
  )
}

function IconMembers() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16" width="16" height="16">
      <circle cx="6" cy="5.5" r="2.5" />
      <path d="M2 13.5c0-2.2 1.8-3.5 4-3.5s4 1.3 4 3.5" strokeLinecap="round" />
      <path d="M11 4.2a2.2 2.2 0 0 1 0 4.1M11.5 10.2c1.6.3 2.5 1.5 2.5 3.3" strokeLinecap="round" />
    </svg>
  )
}

function IconSettings() {
  return (
    <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 16 16" width="16" height="16">
      <circle cx="8" cy="8" r="2.2" />
      <path d="M8 1.5v1.6M8 12.9v1.6M3.4 3.4l1.1 1.1M11.5 11.5l1.1 1.1M1.5 8h1.6M12.9 8h1.6M3.4 12.6l1.1-1.1M11.5 4.5l1.1-1.1" strokeLinecap="round" />
    </svg>
  )
}
