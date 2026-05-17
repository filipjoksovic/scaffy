import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'

type AppFrameProps = {
  children: ReactNode
}

export function AppFrame({ children }: AppFrameProps) {
  return (
    <main className="app-shell">
      <nav aria-label="Primary" className="top-nav">
        <Link aria-label="Scaffy home" className="wordmark" to="/">
          <span aria-hidden="true" className="wordmark-mark" />
          Scaffy
        </Link>
        <div className="nav-links">
          <NavLink end to="/">
            Initializer
          </NavLink>
          <NavLink to="/analyze">Pipeline Analyzer</NavLink>
          <NavLink to="/design">Design Language</NavLink>
        </div>
        <div className="nav-actions">
          <a className="text-link" href="https://github.com/filipjoksovic/scaffy" rel="noreferrer" target="_blank">
            GitHub
          </a>
        </div>
        <button aria-label="Open menu" className="menu-button" type="button">
          <span />
          <span />
        </button>
      </nav>

      {children}

      <footer className="footer">
        <strong>Scaffy</strong>
        <Link to="/">Initializer</Link>
        <Link to="/analyze">Analyzer</Link>
        <Link to="/design">Design</Link>
        <span>CI/CD project tooling</span>
      </footer>
    </main>
  )
}
