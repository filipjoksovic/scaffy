// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApplyFixActions } from '../../src/components/ApplyFixActions'
import type { FindingFixApplyResponse } from '../../src/api/recommend'
import type { FixFinding } from '../../src/lib/recommendations'

const finding: FixFinding = {
  ruleId: 'MISSING_TIMEOUT',
  ruleLabel: 'Missing timeout',
  ruleDescription: 'Each job should set timeout-minutes.',
  dimension: 'workflow_quality',
  capability: 'Execution safety',
  type: 'SMELL',
  evidence: 'timeout-minutes not set',
  location: 'jobs.build',
  startLine: 10,
  endLine: 12,
}

function renderPanel() {
  return render(
    <ApplyFixActions
      finding={finding}
      modifiedContent="name: ci\n"
      runId="run-1"
      workflowPath=".github/workflows/ci.yml"
      workspaceId="workspace-1"
    />,
  )
}

function stubFetch(response: Partial<FindingFixApplyResponse> & { status: FindingFixApplyResponse['status'] }) {
  const body: FindingFixApplyResponse = {
    status: response.status,
    commitSha: response.commitSha ?? null,
    commitUrl: response.commitUrl ?? null,
    branch: response.branch ?? null,
    message: response.message ?? null,
  }
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('ApplyFixActions', () => {
  it('renders the form with the default commit message and an enabled button', () => {
    renderPanel()

    const input = screen.getByLabelText('Commit message') as HTMLInputElement
    expect(input.value).toBe('Improve CI/CD pipeline quality')
    const button = screen.getByRole('button', { name: 'Commit suggested change' }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
  })

  it('disables the submit button when the commit message is cleared', () => {
    renderPanel()

    const input = screen.getByLabelText('Commit message')
    fireEvent.change(input, { target: { value: '   ' } })

    const button = screen.getByRole('button', { name: 'Commit suggested change' }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })

  it('shows a success state with the commit link after a successful apply', async () => {
    stubFetch({
      status: 'ok',
      commitSha: 'abc',
      commitUrl: 'https://example.test/commit/abc',
      branch: 'main',
    })

    renderPanel()
    fireEvent.click(screen.getByRole('button', { name: 'Commit suggested change' }))

    await waitFor(() => {
      expect(screen.getByText('Committed to main.')).toBeTruthy()
    })
    const link = screen.getByRole('link', { name: 'View commit' }) as HTMLAnchorElement
    expect(link.href).toBe('https://example.test/commit/abc')
  })

  it('shows the soft-error state when the response status is unavailable', async () => {
    stubFetch({
      status: 'unavailable',
      message: 'Connect a repository in this workspace before committing AI suggestions.',
    })

    renderPanel()
    fireEvent.click(screen.getByRole('button', { name: 'Commit suggested change' }))

    await waitFor(() => {
      expect(screen.getByText('Commit not applied')).toBeTruthy()
    })
    expect(
      screen.getByText('Connect a repository in this workspace before committing AI suggestions.'),
    ).toBeTruthy()
  })

  it('falls back to the default soft-error message when the response has no message', async () => {
    stubFetch({ status: 'error', message: null })

    renderPanel()
    fireEvent.click(screen.getByRole('button', { name: 'Commit suggested change' }))

    await waitFor(() => {
      expect(screen.getByText('Commit not applied')).toBeTruthy()
    })
    expect(screen.getByText(/Reconnect/)).toBeTruthy()
  })

  it('shows an inline error when the API call throws', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: 'upstream broke' }),
      }),
    )

    renderPanel()
    fireEvent.click(screen.getByRole('button', { name: 'Commit suggested change' }))

    await waitFor(() => {
      expect(screen.getByText('upstream broke')).toBeTruthy()
    })
    const button = screen.getByRole('button', { name: 'Commit suggested change' }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
  })
})
