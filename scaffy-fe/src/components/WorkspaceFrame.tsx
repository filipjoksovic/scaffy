import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { LayoutGrid, Settings, Users } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { Card } from './Card'
import { Eyebrow } from './Eyebrow'
import { TopNav } from './TopNav'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

type WorkspaceFrameProps = {
  children: ReactNode
}

const NAV_ITEMS = [
  { key: 'projects', label: 'Projects', to: '/dashboard', end: true, icon: LayoutGrid },
  { key: 'members', label: 'Members', to: '/workspace/members', end: false, icon: Users },
  { key: 'settings', label: 'Settings', to: '/workspace', end: true, icon: Settings },
] as const

export function WorkspaceFrame({ children }: Readonly<WorkspaceFrameProps>) {
  const { user, loading } = useAuth()

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
              <Link className="button button--primary" to="/login">
                Login
              </Link>
            </div>
          </Card>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell ws-shell">
      <TopNav />
      <div className="ws-body">
        <aside className="ws-sidebar" aria-label="Workspace navigation">
          <WorkspaceSwitcher />
          <nav className="ws-nav" aria-label="Workspace sections">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon
              return (
                <NavLink className="ws-nav__item" end={item.end} key={item.key} to={item.to}>
                  <Icon size={18} strokeWidth={2} />
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
