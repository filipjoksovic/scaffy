import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { currentUser, logout, type CurrentUser } from '../api/auth'

type AuthState = {
  user: CurrentUser | null
  loading: boolean
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setUser(await currentUser())
    } finally {
      setLoading(false)
    }
  }, [])

  const logoutUser = useCallback(async () => {
    await logout()
    setUser(null)
  }, [])

  useEffect(() => {
    void refresh()
  }, [])

  const value = useMemo(() => ({ user, loading, refresh, logout: logoutUser }), [user, loading, refresh, logoutUser])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
