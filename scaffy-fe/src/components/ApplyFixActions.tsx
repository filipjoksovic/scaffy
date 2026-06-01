import { useState } from 'react'
import { applyFindingFix } from '../api/recommend'
import type { FindingFixApplyResponse } from '../api/recommend'
import {
  DEFAULT_COMMIT_MESSAGE,
  applyFixView,
  buildApplyRequest,
  canSubmitCommit,
  type FixFinding,
} from '../lib/recommendations'
import { Button } from './Button'
import { StateRow } from './StateRow'

type ApplyFixState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'success'; result: FindingFixApplyResponse }
  | { kind: 'error'; message: string }

type Props = Readonly<{
  finding: FixFinding
  modifiedContent: string
  runId: string
  workflowPath: string
  workspaceId: string | null
}>

export function ApplyFixActions({
  finding,
  modifiedContent,
  runId,
  workflowPath,
  workspaceId,
}: Props) {
  const [commitMessage, setCommitMessage] = useState(DEFAULT_COMMIT_MESSAGE)
  const [state, setState] = useState<ApplyFixState>({ kind: 'idle' })

  async function handleCommit() {
    setState({ kind: 'submitting' })
    try {
      const result = await applyFindingFix(
        buildApplyRequest({
          runId,
          workflowPath,
          modifiedContent,
          commitMessage,
          finding,
        }),
        workspaceId,
      )
      setState({ kind: 'success', result })
    } catch (error: unknown) {
      setState({
        kind: 'error',
        message:
          error instanceof Error ? error.message : 'Could not commit the suggested change.',
      })
    }
  }

  const view = applyFixView(state)

  if (view.kind === 'success') {
    return (
      <div className="finding-fix__apply finding-fix__apply--success">
        <strong>Committed to {view.branch}.</strong>
        {view.commitUrl ? (
          <a href={view.commitUrl} rel="noreferrer" target="_blank">
            View commit
          </a>
        ) : null}
      </div>
    )
  }

  if (view.kind === 'soft-error') {
    return (
      <StateRow
        detail={view.message ?? ''}
        icon="!"
        label="Commit not applied"
        tone="empty"
      />
    )
  }

  return (
    <div className="finding-fix__apply">
      <label className="finding-fix__apply-field">
        <span>Commit message</span>
        <input
          aria-label="Commit message"
          className="text-input"
          onChange={(event) => setCommitMessage(event.target.value)}
          type="text"
          value={commitMessage}
        />
      </label>
      <div className="finding-fix__apply-actions">
        <Button
          disabled={!canSubmitCommit(state, commitMessage)}
          onClick={handleCommit}
        >
          {view.kind === 'submitting' ? 'Committing...' : 'Commit suggested change'}
        </Button>
        {view.kind === 'error' && (
          <span className="finding-fix__apply-error">{view.message}</span>
        )}
      </div>
    </div>
  )
}
