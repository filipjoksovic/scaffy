import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { TopNav } from './TopNav'

type AppFrameProps = {
  children: ReactNode
}

export function AppFrame({ children }: AppFrameProps) {
  return (
    <main className="app-shell">
      <TopNav />

      {children}

      <footer className="footer">
        <strong>Scaffy</strong>
        <Link to="/">Home</Link>
        <Link to="/dashboard">Projects</Link>
        <Link to="/init">Initializer</Link>
        <Link to="/analyze">Analyzer</Link>
        <Link to="/design">Design</Link>
        <span>CI/CD project tooling</span>
      </footer>
    </main>
  )
}
