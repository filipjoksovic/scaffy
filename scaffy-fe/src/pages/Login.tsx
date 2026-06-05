import { useEffect, useState, type SyntheticEvent } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { Link } from 'react-router-dom'
import {
  addGitlabInstance,
  instanceLoginUrl,
  listGitlabInstances,
  oauthLoginUrl,
  type GitlabInstance,
} from '../api/auth'
import { AppFrame, Eyebrow, ProviderLogo } from '../components'
import { useAuth } from '../lib/auth'

const loginProviders = [
  {
    key: 'google',
    label: 'Continue with Google',
    detail: 'Use your Google account for quick access to Scaffy.',
    href: oauthLoginUrl.google,
  },
  {
    key: 'github',
    label: 'Continue with GitHub',
    detail: 'Sign in with GitHub and connect repositories when you are ready.',
    href: oauthLoginUrl.github,
  },
  {
    key: 'gitlab',
    label: 'Continue with GitLab',
    detail: 'Use GitLab.com or one of the configured GitLab instances.',
    href: oauthLoginUrl.gitlab,
  },
] as const

export function Login() {
  const { user, loading } = useAuth()
  const [instances, setInstances] = useState<GitlabInstance[]>([])
  const [open, setOpen] = useState(false)
  const [baseUrl, setBaseUrl] = useState('')
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [callbackUrl, setCallbackUrl] = useState<string | null>(null)

  const refreshInstances = async () => {
    try {
      setInstances(await listGitlabInstances())
    } catch {
      // Instance discovery is best-effort; the GitLab.com OAuth link still works.
    }
  }

  useEffect(() => {
    let mounted = true
    listGitlabInstances()
      .then((items) => {
        if (mounted) setInstances(items)
      })
      .catch(() => {
        // Instance discovery is best-effort; the GitLab.com OAuth link still works.
      })
    return () => {
      mounted = false
    }
  }, [])

  const resetForm = () => {
    setBaseUrl('')
    setClientId('')
    setClientSecret('')
    setDisplayName('')
    setError(null)
    setCallbackUrl(null)
  }

  const handleSubmit = async (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const result = await addGitlabInstance({
        baseUrl: baseUrl.trim(),
        clientId: clientId.trim(),
        clientSecret: clientSecret.trim(),
        displayName: displayName.trim() || undefined,
      })
      setCallbackUrl(result.callbackUrl)
      await refreshInstances()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add the GitLab instance.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AppFrame>
      <section className="login-screen" aria-labelledby="login-title">
        <div className="login-screen__intro">
          <Eyebrow>Account</Eyebrow>
          <h1 id="login-title">Log in and start working in Scaffy.</h1>
          <p>
            Choose the identity provider your repositories and workspace already use.
            Scaffy will return you to the app after OAuth completes.
          </p>
          <div className="login-screen__signals" aria-label="What login enables">
            <span>Workspaces</span>
            <span>Repository connections</span>
            <span>Project history</span>
          </div>
        </div>

        <div className="login-panel" aria-live="polite">
          {loading ? (
            <div className="login-panel__state">
              <h2>Checking session</h2>
              <p>Verifying whether this browser is already signed in.</p>
            </div>
          ) : user ? (
            <div className="login-panel__state">
              <h2>You are signed in</h2>
              <p>Continue to your workspace to connect repositories and review pipelines.</p>
              <Link className="button button--primary" to="/dashboard">
                Open workspace
              </Link>
            </div>
          ) : (
            <>
              <div className="login-panel__header">
                <h2>Choose a provider</h2>
                <p>OAuth opens in this tab and returns to Scaffy when the backend completes login.</p>
              </div>

              <div className="login-provider-list">
                {loginProviders.map((provider) => (
                  <a className="login-provider" href={provider.href} key={provider.key}>
                    <ProviderLogo provider={provider.key} size={22} />
                    <span>
                      <strong>{provider.label}</strong>
                      <small>{provider.detail}</small>
                    </span>
                  </a>
                ))}
              </div>

              {instances.length > 0 ? (
                <div className="login-instances">
                  <h3>Configured GitLab instances</h3>
                  <div>
                    {instances.map((instance) => (
                      <a
                        className="login-instance-link"
                        href={instanceLoginUrl(instance.registrationId)}
                        key={instance.registrationId}
                      >
                        <ProviderLogo provider="gitlab" size={18} />
                        <span>{instance.displayName || instance.host}</span>
                      </a>
                    ))}
                  </div>
                </div>
              ) : null}

              <Dialog.Root
                onOpenChange={(next) => {
                  setOpen(next)
                  if (!next) resetForm()
                }}
                open={open}
              >
                <Dialog.Trigger asChild>
                  <button className="login-gitlab-instance" type="button">
                    Add a self-hosted GitLab instance
                  </button>
                </Dialog.Trigger>
                <Dialog.Portal>
                  <Dialog.Overlay className="repository-dialog__overlay" />
                  <Dialog.Content className="repository-dialog gitlab-instance-dialog">
                    <header className="repository-dialog__header">
                      <div>
                        <Dialog.Title className="repository-dialog__title">
                          Add a GitLab instance
                        </Dialog.Title>
                        <Dialog.Description className="repository-dialog__description">
                          Register an OAuth application on your GitLab instance with scopes{' '}
                          <code>read_user read_api read_repository</code>, then enter its details.
                        </Dialog.Description>
                      </div>
                      <Dialog.Close aria-label="Close dialog" className="icon-button">
                        <span aria-hidden="true">x</span>
                      </Dialog.Close>
                    </header>

                    {callbackUrl ? (
                      <div className="gitlab-instance-dialog__success">
                        <h4>Instance saved</h4>
                        <p>Register this redirect URI on the GitLab OAuth application:</p>
                        <code className="gitlab-instance-dialog__callback">{callbackUrl}</code>
                        <button
                          className="button button--primary button--small"
                          onClick={() => {
                            setOpen(false)
                            resetForm()
                          }}
                          type="button"
                        >
                          Done
                        </button>
                      </div>
                    ) : (
                      <form className="gitlab-instance-dialog__form" onSubmit={handleSubmit}>
                        <label>
                          Instance URL
                          {' '}
                          <input
                            autoFocus
                            onChange={(event) => setBaseUrl(event.target.value)}
                            placeholder="https://gitlab.example.com"
                            required
                            type="url"
                            value={baseUrl}
                          />
                        </label>
                        <label>
                          Application ID
                          {' '}
                          <input
                            onChange={(event) => setClientId(event.target.value)}
                            required
                            value={clientId}
                          />
                        </label>
                        <label>
                          Secret
                          {' '}
                          <input
                            onChange={(event) => setClientSecret(event.target.value)}
                            required
                            type="password"
                            value={clientSecret}
                          />
                        </label>
                        <label>
                          Display name
                          {' '}
                          <input
                            onChange={(event) => setDisplayName(event.target.value)}
                            placeholder="Company GitLab"
                            value={displayName}
                          />
                        </label>
                        {error ? <p className="form-error">{error}</p> : null}
                        <div className="gitlab-instance-dialog__actions">
                          <Dialog.Close asChild>
                            <button className="button button--secondary button--small" type="button">
                              Cancel
                            </button>
                          </Dialog.Close>
                          <button
                            className="button button--primary button--small"
                            disabled={submitting}
                            type="submit"
                          >
                            {submitting ? 'Saving' : 'Save instance'}
                          </button>
                        </div>
                      </form>
                    )}
                  </Dialog.Content>
                </Dialog.Portal>
              </Dialog.Root>
            </>
          )}
        </div>
      </section>
    </AppFrame>
  )
}
