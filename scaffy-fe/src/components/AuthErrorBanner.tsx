import { useSearchParams } from 'react-router-dom'

/**
 * Surfaces an `authError` query param (set by the backend OAuth failure handler) as a dismissible
 * banner, then strips it from the URL. Rendered once at the router root so it works on any route.
 */
export function AuthErrorBanner() {
  const [params, setParams] = useSearchParams()
  const message = params.get('authError')
  if (!message) {
    return null
  }

  function dismiss() {
    const next = new URLSearchParams(params)
    next.delete('authError')
    setParams(next, { replace: true })
  }

  return (
    <div className="auth-error-banner" role="alert">
      <span aria-hidden="true" className="auth-error-banner__icon">
        !
      </span>
      <p>{message}</p>
      <button aria-label="Dismiss" onClick={dismiss} type="button">
        ×
      </button>
    </div>
  )
}
