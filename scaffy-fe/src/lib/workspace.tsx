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

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [activeId, setActiveId] = useState<string | null>(() => readStoredId())
  const [loading, setLoading] = useState(false)

  const applyActiveId = useCallback((workspaceId: string | null) => {
    setActiveId(workspaceId)
    setActiveWorkspaceId(workspaceId)
    try {
      if (workspaceId) {
        localStorage.setItem(STORAGE_KEY, workspaceId)
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // ignore storage failures
    }
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listWorkspaces()
      setWorkspaces(list)
      setActiveId((current) => {
        const next = list.some((workspace) => workspace.id === current)
          ? current
          : (list[0]?.id ?? null)
        setActiveWorkspaceId(next)
        try {
          if (next) {
            localStorage.setItem(STORAGE_KEY, next)
          } else {
            localStorage.removeItem(STORAGE_KEY)
          }
        } catch {
          // ignore storage failures
        }
        return next
      })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (user) {
      void refresh()
    } else {
      setWorkspaces([])
      applyActiveId(null)
    }
  }, [user, refresh, applyActiveId])

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

export function useWorkspace() {
  const context = useContext(WorkspaceContext)
  if (context === null) {
    throw new Error('useWorkspace must be used inside WorkspaceProvider')
  }
  return context
}
