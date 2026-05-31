import { useCallback, useEffect, useState } from "react";
import type { SyntheticEvent } from "react";
import {
  getWorkspace,
  inviteMember,
  removeMember,
  revokeInvitation,
  type WorkspaceDetail,
} from "../api/workspaces";
import { Badge, Button, Card, Eyebrow, StateRow, TextInput, WorkspaceFrame } from "../components";
import { useAuth } from "../lib/auth";
import { useWorkspace } from "../lib/workspace";

export function WorkspaceMembers() {
  const { user } = useAuth();
  const { activeWorkspace, refresh: refreshWorkspaces } = useWorkspace();
  const activeWorkspaceId = activeWorkspace?.id ?? null;

  const [detail, setDetail] = useState<WorkspaceDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviting, setInviting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);

  const isOwner = detail?.role === "owner";

  const loadDetail = useCallback(async () => {
    if (!activeWorkspaceId) {
      setDetail(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setDetail(await getWorkspace(activeWorkspaceId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load members.");
    } finally {
      setLoading(false);
    }
  }, [activeWorkspaceId]);

  useEffect(() => {
    if (user) {
      // Mount fetch via a reused loader; its loading flag is set synchronously by design.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadDetail();
    }
  }, [user, loadDetail]);

  async function handleInvite(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activeWorkspaceId || !inviteEmail.trim()) {
      return;
    }
    setInviting(true);
    setInviteError(null);
    try {
      await inviteMember(activeWorkspaceId, inviteEmail.trim());
      setInviteEmail("");
      await loadDetail();
    } catch (err) {
      setInviteError(err instanceof Error ? err.message : "Could not send invitation.");
    } finally {
      setInviting(false);
    }
  }

  async function handleRevoke(invitationId: string) {
    if (!activeWorkspaceId) return;
    try {
      await revokeInvitation(activeWorkspaceId, invitationId);
      await loadDetail();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not revoke invitation.");
    }
  }

  async function handleRemove(userId: string) {
    if (!activeWorkspaceId) return;
    try {
      await removeMember(activeWorkspaceId, userId);
      await Promise.all([loadDetail(), refreshWorkspaces()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove member.");
    }
  }

  const detailContent = detail ? (
    <div className="ws-stack">
      {isOwner && (
        <Card as="section" className="workspace-card">
          <Eyebrow>Invite</Eyebrow>
          <h3>Invite a teammate</h3>
          <p className="workspace-card__hint">
            They join as a member and can connect repositories and view analyses.
          </p>
          <form className="workspace-form" onSubmit={handleInvite}>
            <TextInput
              onChange={(event) => setInviteEmail(event.target.value)}
              placeholder="teammate@example.com"
              type="email"
              value={inviteEmail}
            />
            <Button disabled={inviting} type="submit">
              {inviting ? "Sending…" : "Send invite"}
            </Button>
          </form>
          {inviteError && <p className="form-error">{inviteError}</p>}
        </Card>
      )}

      <Card as="section" className="workspace-card">
        <Eyebrow>People</Eyebrow>
        <h3>{detail.members.length} members</h3>
        <ul className="workspace-member-list">
          {detail.members.map((member) => {
            const label = member.displayName || member.email;
            const isSelf = member.userId === user?.id;
            const canRemove =
              member.role !== "owner" && (isOwner || isSelf);
            return (
              <li className="workspace-member-list__row" key={member.userId}>
                <div className="workspace-member-list__id">
                  {member.avatarUrl ? (
                    <img alt="" className="account-menu__avatar" src={member.avatarUrl} />
                  ) : (
                    <span
                      aria-hidden="true"
                      className="account-menu__avatar account-menu__avatar--fallback"
                    >
                      {label.charAt(0).toUpperCase()}
                    </span>
                  )}
                  <div>
                    <strong>
                      {label}
                      {isSelf && <span className="workspace-tag">You</span>}
                    </strong>
                    <span>{member.email}</span>
                  </div>
                </div>
                <div className="workspace-member-list__actions">
                  <Badge>{member.role}</Badge>
                  {canRemove && (
                    <Button
                      className="button--small"
                      onClick={() => void handleRemove(member.userId)}
                      variant="secondary"
                    >
                      {isSelf ? "Leave" : "Remove"}
                    </Button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </Card>

      {isOwner && detail.invitations.length > 0 && (
        <Card as="section" className="workspace-card">
          <Eyebrow>Pending</Eyebrow>
          <h3>Pending invitations</h3>
          <ul className="workspace-invite-list">
            {detail.invitations.map((invitation) => (
              <li className="workspace-invite-list__row" key={invitation.id}>
                <div>
                  <strong>{invitation.email}</strong>
                  <span>invited as {invitation.role}</span>
                </div>
                <Button
                  className="button--small"
                  onClick={() => void handleRevoke(invitation.id)}
                  variant="secondary"
                >
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  ) : (
    <StateRow
      detail="Select or create a workspace to manage members."
      label="No workspace selected"
      tone="empty"
    />
  );

  return (
    <WorkspaceFrame>
      <section className="ws-page" aria-labelledby="members-title">
        <header className="ws-page__header">
          <div className="ws-page__heading">
            <Eyebrow>{activeWorkspace?.name ?? "Workspace"}</Eyebrow>
            <h2 id="members-title">Members</h2>
            <p className="ws-page__status">
              <span>{detail ? `${detail.members.length} active` : "—"}</span>
            </p>
          </div>
        </header>

        {error && (
          <StateRow detail={error} icon="!" label="Something went wrong" tone="error" />
        )}

        {loading && !detail ? (
          <StateRow
            detail="Loading the people in this workspace."
            label="Loading members"
            tone="loading"
          />
        ) : (
          detailContent
        )}
      </section>
    </WorkspaceFrame>
  );
}
