import * as Popover from '@radix-ui/react-popover'
import { Link, NavLink } from 'react-router-dom'
import { oauthLoginUrl } from '../api/auth'
import { useAuth } from '../lib/auth'
import { GitlabLoginMenu } from './GitlabLoginMenu'

export function TopNav() {
  const { user, loading, logout } = useAuth()
  const name = user?.displayName || user?.email || 'Account'
  const initial = name.trim().charAt(0).toUpperCase() || '?'

  let authContent
  if (loading) {
    authContent = <span className="auth-status">Checking session</span>
  } else if (user) {
    authContent = (
      <Popover.Root>
        <Popover.Trigger asChild>
          <button
            aria-label={`Account menu for ${name}`}
            className="account-trigger"
            type="button"
          >
            {user.avatarUrl ? (
              <img alt="" className="account-trigger__avatar" src={user.avatarUrl} />
            ) : (
              <span aria-hidden="true" className="account-trigger__avatar account-trigger__avatar--fallback">
                {initial}
              </span>
            )}
            <span className="account-trigger__name">{name}</span>
            <svg
              aria-hidden="true"
              className="account-trigger__chev"
              viewBox="0 0 16 16"
              width="12"
              height="12"
            >
              <path d="M3.5 6l4.5 4 4.5-4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content
            align="end"
            className="account-menu"
            collisionPadding={12}
            sideOffset={8}
          >
            <div className="account-menu__head">
              {user.avatarUrl ? (
                <img alt="" className="account-menu__avatar" src={user.avatarUrl} />
              ) : (
                <span aria-hidden="true" className="account-menu__avatar account-menu__avatar--fallback">
                  {initial}
                </span>
              )}
              <div>
                <strong>{name}</strong>
                {user.email && user.email !== name && <span>{user.email}</span>}
              </div>
            </div>
            <div className="account-menu__items">
              <Popover.Close asChild>
                <Link className="account-menu__item" to="/dashboard">
                  <span>Projects</span>
                </Link>
              </Popover.Close>
              <Popover.Close asChild>
                <Link className="account-menu__item" to="/workspace/members">
                  <span>Members</span>
                </Link>
              </Popover.Close>
              <Popover.Close asChild>
                <Link className="account-menu__item" to="/workspace">
                  <span>Workspace settings</span>
                </Link>
              </Popover.Close>
              <button
                className="account-menu__item account-menu__item--danger"
                onClick={() => void logout()}
                type="button"
              >
                <span>Log out</span>
              </button>
            </div>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
    )
  } else {
    authContent = (
      <div className="auth-menu">
        <a className="text-link" href={oauthLoginUrl.google}>
          Google login
        </a>
        <a className="text-link" href={oauthLoginUrl.github}>
          GitHub login
        </a>
        <GitlabLoginMenu />
      </div>
    )
  }

  return (
    <nav aria-label="Primary" className="top-nav">
      <Link aria-label="Scaffy home" className="wordmark" to="/">
        <img alt="" className="wordmark-mark" src="/scaffy-logo.png" />
        {' '}
        Scaffy
      </Link>
      <div className="nav-links">
        <NavLink end to="/">
          Home
        </NavLink>
        {user && <NavLink to="/dashboard">Projects</NavLink>}
        <NavLink to="/init">Initializer</NavLink>
        <NavLink to="/analyze">Pipeline Analyzer</NavLink>
        <NavLink to="/design">Design Language</NavLink>
      </div>
      <div className="nav-actions">{authContent}</div>
      <button aria-label="Open menu" className="menu-button" type="button">
        <span />
        <span />
      </button>
    </nav>
  )
}
