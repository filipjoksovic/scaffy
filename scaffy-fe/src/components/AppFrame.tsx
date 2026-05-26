import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { oauthLoginUrl } from '../api/auth'
import { useAuth } from '../lib/auth'

type AppFrameProps = {
  children: ReactNode
}

export function AppFrame({ children }: AppFrameProps) {
  const { user, loading, logout } = useAuth()
  const name = user?.displayName || user?.email || 'Account'

  return (
    <main className="app-shell">
      <nav aria-label="Primary" className="top-nav">
        <Link aria-label="Scaffy home" className="wordmark" to="/">
          <span aria-hidden="true" className="wordmark-mark" />{" "}
          Scaffy
        </Link>
        <div className="nav-links">
          <NavLink end to="/">
            Home
          </NavLink>
          <NavLink to="/init">
            Initializer
          </NavLink>
          <NavLink to="/analyze">Pipeline Analyzer</NavLink>
          <NavLink to="/design">Design Language</NavLink>
        </div>
        <div className="nav-actions">
          {loading ? (
            <span className="auth-status">Checking session</span>
          ) : user ? (
            <div className="auth-menu">
              {user.avatarUrl && <img alt="" src={user.avatarUrl} />}
              <span>{name}</span>
              <button className="text-link" onClick={() => void logout()} type="button">
                Log out
              </button>
            </div>
          ) : (
            <div className="auth-menu">
              <a className="text-link" href={oauthLoginUrl.google}>
                Google login
              </a>
              <a className="text-link" href={oauthLoginUrl.github}>
                GitHub login
              </a>
            </div>
          )}
        </div>
        <button aria-label="Open menu" className="menu-button" type="button">
          <span />
          <span />
        </button>
      </nav>

      {children}

      <footer className="footer">
        <strong>Scaffy</strong>
        <Link to="/">Home</Link>
        <Link to="/init">Initializer</Link>
        <Link to="/analyze">Analyzer</Link>
        <Link to="/design">Design</Link>
        <span>CI/CD project tooling</span>
      </footer>
    </main>
  )
}
