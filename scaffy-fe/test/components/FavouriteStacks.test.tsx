// @vitest-environment jsdom
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FavouriteStacks } from '../../src/components/FavouriteStacks'
import type { FavouriteStack, FavouriteStackRequest } from '../../src/api/init'

// ---------------------------------------------------------------------------
// Module mock
// ---------------------------------------------------------------------------

vi.mock('../../src/api/init', () => ({
  getFavouriteStacks: vi.fn(),
  saveFavouriteStack: vi.fn(),
  deleteFavouriteStack: vi.fn(),
}))

import {
  deleteFavouriteStack,
  getFavouriteStacks,
  saveFavouriteStack,
} from '../../src/api/init'

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const currentSelection: FavouriteStackRequest = {
  name: '',
  frontend: 'react',
  frontendVersion: '19',
  frontendRuntime: 'node-22',
  backend: 'spring-boot',
  backendVersion: '4.0',
  backendRuntime: 'java-21',
  pipeline: 'github-actions',
  pipelineMaturity: 'l2',
  includeDocker: false,
}

const mockFav: FavouriteStack = {
  id: 'fav-1',
  userId: 'user-1',
  name: 'My React + Spring',
  frontend: 'react',
  frontendVersion: '19',
  frontendRuntime: 'node-22',
  backend: 'spring-boot',
  backendVersion: '4.0',
  backendRuntime: 'java-21',
  pipeline: 'github-actions',
  pipelineMaturity: 'l2',
  includeDocker: false,
  createdAt: '2026-01-01T12:00:00Z',
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderComponent(props: Partial<Parameters<typeof FavouriteStacks>[0]> = {}) {
  const onLoad = vi.fn()
  const result = render(
    <FavouriteStacks
      canSave={false}
      currentSelection={currentSelection}
      onLoad={onLoad}
      {...props}
    />,
  )
  return { onLoad, ...result }
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  vi.mocked(getFavouriteStacks).mockResolvedValue([])
  vi.mocked(saveFavouriteStack).mockResolvedValue(mockFav)
  vi.mocked(deleteFavouriteStack).mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('FavouriteStacks', () => {
  it('renders the Favourites heading', async () => {
    const { container } = renderComponent()
    await waitFor(() => {
      expect(container.querySelector('#favourites-heading')).toBeTruthy()
    })
  })

  it('shows empty-state hint when authenticated but no favourites saved', async () => {
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText(/complete your stack selection/i)).toBeTruthy()
    })
  })

  it('shows "save current" button when canSave is true and list is empty', async () => {
    const { getByRole } = renderComponent({ canSave: true })
    await waitFor(() => {
      expect(getByRole('button', { name: /save current/i })).toBeTruthy()
    })
  })

  it('changes empty hint to "click save current" when canSave is true', async () => {
    const { getByText } = renderComponent({ canSave: true })
    await waitFor(() => {
      expect(getByText(/click.*save current/i)).toBeTruthy()
    })
  })

  it('shows sign-in hint when the API returns a 401 error', async () => {
    vi.mocked(getFavouriteStacks).mockRejectedValue(new Error('401 Unauthorized'))
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText(/sign in/i)).toBeTruthy()
    })
  })

  it('shows sign-in hint when error message contains "unauthori"', async () => {
    vi.mocked(getFavouriteStacks).mockRejectedValue(new Error('Request unauthorised'))
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText(/sign in/i)).toBeTruthy()
    })
  })

  it('silently ignores non-auth errors and shows empty state', async () => {
    vi.mocked(getFavouriteStacks).mockRejectedValue(new Error('Network error'))
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText(/complete your stack selection/i)).toBeTruthy()
    })
  })

  it('renders saved favourites in a list', async () => {
    vi.mocked(getFavouriteStacks).mockResolvedValue([mockFav])
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText('My React + Spring')).toBeTruthy()
    })
  })

  it('shows stack meta for each favourite', async () => {
    vi.mocked(getFavouriteStacks).mockResolvedValue([mockFav])
    const { getByText } = renderComponent()
    await waitFor(() => {
      expect(getByText(/react · spring-boot · github-actions/i)).toBeTruthy()
    })
  })

  it('calls onLoad with the favourite when Load is clicked', async () => {
    vi.mocked(getFavouriteStacks).mockResolvedValue([mockFav])
    const { onLoad, getByRole } = renderComponent()
    await waitFor(() => getByRole('button', { name: /^load$/i }))
    fireEvent.click(getByRole('button', { name: /^load$/i }))
    expect(onLoad).toHaveBeenCalledWith(mockFav)
  })

  it('removes a favourite from the list when delete is clicked', async () => {
    vi.mocked(getFavouriteStacks).mockResolvedValue([mockFav])
    const { getByText, queryByText, getByRole } = renderComponent()
    await waitFor(() => getByText('My React + Spring'))
    fireEvent.click(getByRole('button', { name: /remove my react/i }))
    await waitFor(() => {
      expect(queryByText('My React + Spring')).toBeNull()
    })
  })

  it('opens the save form when "★ Save current" is clicked', async () => {
    const { getByRole, getByPlaceholderText } = renderComponent({ canSave: true })
    await waitFor(() => getByRole('button', { name: /save current/i }))
    fireEvent.click(getByRole('button', { name: /save current/i }))
    expect(getByPlaceholderText(/my react/i)).toBeTruthy()
  })

  it('saves a new favourite and calls the API with the given name', async () => {
    const { getByRole, getByPlaceholderText } = renderComponent({ canSave: true })
    await waitFor(() => getByRole('button', { name: /save current/i }))
    fireEvent.click(getByRole('button', { name: /save current/i }))
    fireEvent.change(getByPlaceholderText(/my react/i), { target: { value: 'New Favourite' } })
    fireEvent.click(getByRole('button', { name: /^save$/i }))
    await waitFor(() => {
      expect(saveFavouriteStack).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'New Favourite' }),
      )
    })
  })

  it('closes the save form on Cancel', async () => {
    const { getByRole, queryByPlaceholderText } = renderComponent({ canSave: true })
    await waitFor(() => getByRole('button', { name: /save current/i }))
    fireEvent.click(getByRole('button', { name: /save current/i }))
    fireEvent.click(getByRole('button', { name: /cancel/i }))
    expect(queryByPlaceholderText(/my react/i)).toBeNull()
  })

  it('shows an error message when saving fails', async () => {
    vi.mocked(saveFavouriteStack).mockRejectedValue(new Error('Limit reached'))
    const { getByRole, getByText, getByPlaceholderText } = renderComponent({ canSave: true })
    await waitFor(() => getByRole('button', { name: /save current/i }))
    fireEvent.click(getByRole('button', { name: /save current/i }))
    fireEvent.change(getByPlaceholderText(/my react/i), { target: { value: 'Too Many' } })
    fireEvent.click(getByRole('button', { name: /^save$/i }))
    await waitFor(() => {
      expect(getByText(/limit reached/i)).toBeTruthy()
    })
  })
})
