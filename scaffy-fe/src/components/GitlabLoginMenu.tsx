import { useEffect, useState, type SyntheticEvent } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import {
  addGitlabInstance,
  instanceLoginUrl,
  listGitlabInstances,
  oauthLoginUrl,
  type GitlabInstance,
} from '../api/auth'

export function GitlabLoginMenu() {
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
      // listing is best-effort; the login links still work without it
    }
  }

  useEffect(() => {
    let mounted = true
    listGitlabInstances()
      .then((items) => {
        if (mounted) setInstances(items)
      })
      .catch(() => {
        // listing is best-effort; the login links still work without it
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
    <>
      <a className="text-link" href={oauthLoginUrl.gitlab}>
        GitLab login
      </a>
      {instances.map((instance) => (
        <a
          className="text-link"
          href={instanceLoginUrl(instance.registrationId)}
          key={instance.registrationId}
        >
          {instance.displayName || instance.host} login
        </a>
      ))}
      <Dialog.Root
        onOpenChange={(next) => {
          setOpen(next)
          if (!next) resetForm()
        }}
        open={open}
      >
        <Dialog.Trigger asChild>
          <button className="text-link" type="button">
            Add GitLab instance
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
                  Register an OAuth application on your GitLab instance (scopes{' '}
                  <code>read_user read_api read_repository</code>), then enter its details below.
                </Dialog.Description>
              </div>
              <Dialog.Close aria-label="Close dialog" className="icon-button">
                <span aria-hidden="true">×</span>
              </Dialog.Close>
            </header>

            {callbackUrl ? (
              <div className="gitlab-instance-dialog__success">
                <h4>Instance saved</h4>
                <p>
                  Make sure this exact redirect URI is registered on your GitLab OAuth application:
                </p>
                <code className="gitlab-instance-dialog__callback">{callbackUrl}</code>
                <p>You can now close this dialog and use the new GitLab login link.</p>
                <button
                  className="button button--secondary button--small"
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
                  Application ID (client id)
                  {' '}
                  <input
                    onChange={(event) => setClientId(event.target.value)}
                    required
                    value={clientId}
                  />
                </label>
                <label>
                  Secret (client secret)
                  <input
                    onChange={(event) => setClientSecret(event.target.value)}
                    required
                    type="password"
                    value={clientSecret}
                  />
                </label>
                <label>
                  Display name (optional)
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
  )
}
