import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { listWorkspaces, type Workspace } from '../api/workspaces'
import { setActiveWorkspaceId } from '../api/client'
import { useAuth } from './auth'

const STORAGE_KEY = 'scaffy.activeWorkspaceId'

type WorkspaceState = {
  workspaces: Workspace[]
  activeWorkspace: Workspace | null
  loading: boolean
  selectWorkspace: (workspaceId: string) => void
  refresh: () => Promise<void>
}

const WorkspaceContext = createContext<WorkspaceState | null>(null)

function readStoredId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeStoredId(workspaceId: string | null) {
  try {
    if (workspaceId) {
      localStorage.setItem(STORAGE_KEY, workspaceId)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // ignore storage failures
  }
}

function pickActiveId(list: Workspace[]): string | null {
  const stored = readStoredId()
  return list.some((workspace) => workspace.id === stored) ? stored : (list[0]?.id ?? null)
}

export function WorkspaceProvider({ children }: Readonly<{ children: ReactNode }>) {
  const { user, loading: authLoading } = useAuth()
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [activeId, setActiveId] = useState<string | null>(() => {
    // Seed the outgoing X-Workspace-Id header from storage before any request fires.
    const stored = readStoredId()
    setActiveWorkspaceId(stored)
    return stored
  })
  const [loading, setLoading] = useState(false)

  // persist = false is used for logout, where we clear the in-memory selection + header but keep
  // the stored id so the same workspace is restored on the next sign-in.
  const applyActiveId = useCallback((workspaceId: string | null, persist = true) => {
    setActiveId(workspaceId)
    setActiveWorkspaceId(workspaceId)
    if (persist) {
      writeStoredId(workspaceId)
    }
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listWorkspaces()
      setWorkspaces(list)
      applyActiveId(pickActiveId(list))
    } finally {
      setLoading(false)
    }
  }, [applyActiveId])

  useEffect(() => {
    if (!user) {
      if (!authLoading) {
        // Genuinely signed out (not just the initial auth check) — clear in-memory state without
        // wiping the stored selection. Sync state reset on auth change is intentional here.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setWorkspaces([])
        applyActiveId(null, false)
      }
      return
    }
    let mounted = true
    setLoading(true)
    listWorkspaces()
      .then((list) => {
        if (!mounted) return
        setWorkspaces(list)
        applyActiveId(pickActiveId(list))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [user, authLoading, applyActiveId])

  const selectWorkspace = useCallback(
    (workspaceId: string) => {
      applyActiveId(workspaceId)
    },
    [applyActiveId],
  )

  const activeWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.id === activeId) ?? null,
    [workspaces, activeId],
  )

  const value = useMemo(
    () => ({ workspaces, activeWorkspace, loading, selectWorkspace, refresh }),
    [workspaces, activeWorkspace, loading, selectWorkspace, refresh],
  )

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

// Context hook co-located with its provider; splitting it out would churn every importer.
// eslint-disable-next-line react-refresh/only-export-components
export function useWorkspace() {
  const context = useContext(WorkspaceContext)
  if (context === null) {
    throw new Error('useWorkspace must be used inside WorkspaceProvider')
  }
  return context
}
