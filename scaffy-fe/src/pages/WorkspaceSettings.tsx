import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  acceptInvitation,
  addWorkspaceGitlabInstance,
  deleteWorkspaceGitlabInstance,
  getWorkspace,
  listMyInvitations,
  listWorkspaceGitlabInstances,
  renameWorkspace,
  type WorkspaceDetail,
  type WorkspaceGitlabInstance,
  type WorkspaceInvitation,
} from "../api/workspaces";
import {
  connectProviderUrl,
  disconnectProvider,
  listConnections,
  type ProviderConnection,
} from "../api/auth";
import { Badge, Button, Card, Eyebrow, StateRow, TextInput, WorkspaceFrame } from "../components";
import { useAuth } from "../lib/auth";
import { useWorkspace } from "../lib/workspace";

export function WorkspaceSettings() {
  const { user } = useAuth();
  const { activeWorkspace, refresh: refreshWorkspaces } = useWorkspace();
  const activeWorkspaceId = activeWorkspace?.id ?? null;

  const [detail, setDetail] = useState<WorkspaceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [renameValue, setRenameValue] = useState("");
  const [savingName, setSavingName] = useState(false);

  const [incoming, setIncoming] = useState<WorkspaceInvitation[]>([]);
  const [acceptingToken, setAcceptingToken] = useState<string | null>(null);

  const [providerConnections, setProviderConnections] = useState<ProviderConnection[]>([]);
  const [instances, setInstances] = useState<WorkspaceGitlabInstance[]>([]);
  const [instanceForm, setInstanceForm] = useState({
    baseUrl: "",
    clientId: "",
    clientSecret: "",
    displayName: "",
  });
  const [addingInstance, setAddingInstance] = useState(false);
  const [instanceError, setInstanceError] = useState<string | null>(null);
  const [callbackUrl, setCallbackUrl] = useState<string | null>(null);

  const isOwner = detail?.role === "owner";

  const loadDetail = useCallback(async () => {
    if (!activeWorkspaceId) {
      setDetail(null);
      return;
    }
    setDetailLoading(true);
    setError(null);
    try {
      const next = await getWorkspace(activeWorkspaceId);
      setDetail(next);
      setRenameValue(next.name);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load workspace.");
    } finally {
      setDetailLoading(false);
    }
  }, [activeWorkspaceId]);

  const loadIncoming = useCallback(async () => {
    try {
      setIncoming(await listMyInvitations());
    } catch {
      setIncoming([]);
    }
  }, []);

  const loadInstances = useCallback(async () => {
    if (!activeWorkspaceId) {
      setInstances([]);
      return;
    }
    try {
      setInstances(await listWorkspaceGitlabInstances(activeWorkspaceId));
    } catch {
      setInstances([]);
    }
  }, [activeWorkspaceId]);

  const loadProviderConnections = useCallback(async () => {
    try {
      setProviderConnections(await listConnections());
    } catch {
      setProviderConnections([]);
    }
  }, []);

  useEffect(() => {
    if (user) {
      void loadDetail();
      void loadIncoming();
      void loadInstances();
      void loadProviderConnections();
    }
  }, [user, loadDetail, loadIncoming, loadInstances, loadProviderConnections]);

  async function handleDisconnectProvider(provider: string, instance: string) {
    try {
      await disconnectProvider(provider, instance);
      await loadProviderConnections();
    } catch {
      // ignore — list refresh will reflect server state
    }
  }

  const githubConnection = providerConnections.find((c) => c.provider === "github");
  const gitlabComConnection = providerConnections.find(
    (c) => c.provider === "gitlab" && (c.instance === "gitlab.com" || c.instance === ""),
  );

  async function handleAddInstance(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activeWorkspaceId || !instanceForm.baseUrl.trim()) {
      return;
    }
    setAddingInstance(true);
    setInstanceError(null);
    try {
      const result = await addWorkspaceGitlabInstance(activeWorkspaceId, {
        baseUrl: instanceForm.baseUrl.trim(),
        clientId: instanceForm.clientId.trim(),
        clientSecret: instanceForm.clientSecret.trim(),
        displayName: instanceForm.displayName.trim() || undefined,
      });
      setCallbackUrl(result.callbackUrl);
      setInstanceForm({ baseUrl: "", clientId: "", clientSecret: "", displayName: "" });
      await loadInstances();
    } catch (err) {
      setInstanceError(err instanceof Error ? err.message : "Could not add instance.");
    } finally {
      setAddingInstance(false);
    }
  }

  async function handleDeleteInstance(instanceId: string) {
    if (!activeWorkspaceId) return;
    try {
      await deleteWorkspaceGitlabInstance(activeWorkspaceId, instanceId);
      await loadInstances();
    } catch (err) {
      setInstanceError(err instanceof Error ? err.message : "Could not remove instance.");
    }
  }

  async function handleRename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activeWorkspaceId || !renameValue.trim()) {
      return;
    }
    setSavingName(true);
    setError(null);
    try {
      await renameWorkspace(activeWorkspaceId, renameValue.trim());
      await Promise.all([loadDetail(), refreshWorkspaces()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not rename workspace.");
    } finally {
      setSavingName(false);
    }
  }

  async function handleAccept(token: string) {
    setAcceptingToken(token);
    try {
      await acceptInvitation(token);
      await Promise.all([refreshWorkspaces(), loadIncoming()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not accept invitation.");
    } finally {
      setAcceptingToken(null);
    }
  }

  return (
    <WorkspaceFrame active="settings">
      <section className="ws-page" aria-labelledby="settings-title">
        <header className="ws-page__header">
          <div className="ws-page__heading">
            <Eyebrow>{activeWorkspace?.name ?? "Workspace"}</Eyebrow>
            <h2 id="settings-title">Settings</h2>
          </div>
        </header>

        {error && (
          <StateRow detail={error} icon="!" label="Something went wrong" tone="error" />
        )}

        {incoming.length > 0 && (
          <Card as="section" className="workspace-card workspace-card--accent">
            <Eyebrow>Invitations</Eyebrow>
            <h3>You have been invited</h3>
            <ul className="workspace-invite-list">
              {incoming.map((invitation) => (
                <li className="workspace-invite-list__row" key={invitation.id}>
                  <div>
                    <strong>{invitation.workspaceName}</strong>
                    <span>join as {invitation.role}</span>
                  </div>
                  <Button
                    className="button--small"
                    disabled={acceptingToken === invitation.token}
                    onClick={() => void handleAccept(invitation.token)}
                  >
                    {acceptingToken === invitation.token ? "Joining…" : "Accept"}
                  </Button>
                </li>
              ))}
            </ul>
          </Card>
        )}

        {detailLoading && !detail ? (
          <StateRow
            detail="Loading workspace settings."
            label="Loading workspace"
            tone="loading"
          />
        ) : detail ? (
          <div className="ws-stack">
            <Card as="section" className="workspace-card">
              <Eyebrow>General</Eyebrow>
              <h3>Workspace name</h3>
              <form className="workspace-form" onSubmit={handleRename}>
                <TextInput
                  disabled={!isOwner}
                  onChange={(event) => setRenameValue(event.target.value)}
                  value={renameValue}
                />
                {isOwner && (
                  <Button disabled={savingName} type="submit">
                    {savingName ? "Saving…" : "Save"}
                  </Button>
                )}
              </form>
              {!isOwner && (
                <p className="workspace-card__hint">
                  Only workspace owners can rename the workspace.
                </p>
              )}
            </Card>

            <Card as="section" className="workspace-card">
              <Eyebrow>Connections</Eyebrow>
              <h3>Your repository accounts</h3>
              <p className="workspace-card__hint">
                Connect a provider to grant Scaffy access to your repositories. Connecting is
                personal to your account; after connecting you can add projects to this workspace.
              </p>
              <ul className="gitlab-instance-list">
                <li className="gitlab-instance-list__row">
                  <div className="gitlab-instance-list__id">
                    <strong>GitHub</strong>
                    <span>{githubConnection ? "Connected" : "Not connected"}</span>
                  </div>
                  <div className="gitlab-instance-list__actions">
                    {githubConnection ? (
                      <Button
                        className="button--small"
                        onClick={() => void handleDisconnectProvider("github", "")}
                        variant="secondary"
                      >
                        Disconnect
                      </Button>
                    ) : (
                      <a
                        className="button button--primary button--small"
                        href={connectProviderUrl("github-repos")}
                      >
                        Connect GitHub
                      </a>
                    )}
                  </div>
                </li>
                <li className="gitlab-instance-list__row">
                  <div className="gitlab-instance-list__id">
                    <strong>GitLab.com</strong>
                    <span>{gitlabComConnection ? "Connected" : "Not connected"}</span>
                  </div>
                  <div className="gitlab-instance-list__actions">
                    {gitlabComConnection ? (
                      <Button
                        className="button--small"
                        onClick={() => void handleDisconnectProvider("gitlab", "gitlab.com")}
                        variant="secondary"
                      >
                        Disconnect
                      </Button>
                    ) : (
                      <a
                        className="button button--primary button--small"
                        href={connectProviderUrl("gitlab")}
                      >
                        Connect GitLab.com
                      </a>
                    )}
                  </div>
                </li>
              </ul>
            </Card>

            <Card as="section" className="workspace-card">
              <Eyebrow>Details</Eyebrow>
              <h3>Workspace details</h3>
              <dl className="workspace-detail-list">
                <div>
                  <dt>Slug</dt>
                  <dd>
                    <code>{detail.slug}</code>
                  </dd>
                </div>
                <div>
                  <dt>Your role</dt>
                  <dd>{detail.role}</dd>
                </div>
                <div>
                  <dt>Members</dt>
                  <dd>{detail.members.length}</dd>
                </div>
              </dl>
            </Card>

            <Card as="section" className="workspace-card">
              <Eyebrow>Integrations</Eyebrow>
              <h3>Self-hosted GitLab</h3>
              <p className="workspace-card__hint">
                Register a self-hosted GitLab instance for this workspace. Each member then
                connects their own account before adding projects.
              </p>

              {instances.length > 0 && (
                <ul className="gitlab-instance-list">
                  {instances.map((instance) => (
                    <li className="gitlab-instance-list__row" key={instance.id}>
                      <div className="gitlab-instance-list__id">
                        <strong>{instance.displayName || instance.host}</strong>
                        <span>{instance.host}</span>
                      </div>
                      <div className="gitlab-instance-list__actions">
                        {instance.connected ? (
                          <Badge>Connected</Badge>
                        ) : (
                          <a
                            className="button button--secondary button--small"
                            href={connectProviderUrl(instance.registrationId)}
                          >
                            Connect
                          </a>
                        )}
                        {isOwner && (
                          <Button
                            className="button--small"
                            onClick={() => void handleDeleteInstance(instance.id)}
                            variant="secondary"
                          >
                            Remove
                          </Button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              {isOwner && (
                <form className="gitlab-instance-form" onSubmit={handleAddInstance}>
                  <TextInput
                    onChange={(event) =>
                      setInstanceForm((current) => ({ ...current, baseUrl: event.target.value }))
                    }
                    placeholder="https://gitlab.example.com"
                    value={instanceForm.baseUrl}
                  />
                  <TextInput
                    onChange={(event) =>
                      setInstanceForm((current) => ({ ...current, displayName: event.target.value }))
                    }
                    placeholder="Display name (optional)"
                    value={instanceForm.displayName}
                  />
                  <TextInput
                    onChange={(event) =>
                      setInstanceForm((current) => ({ ...current, clientId: event.target.value }))
                    }
                    placeholder="OAuth application ID"
                    value={instanceForm.clientId}
                  />
                  <TextInput
                    onChange={(event) =>
                      setInstanceForm((current) => ({ ...current, clientSecret: event.target.value }))
                    }
                    placeholder="OAuth application secret"
                    type="password"
                    value={instanceForm.clientSecret}
                  />
                  <Button disabled={addingInstance} type="submit">
                    {addingInstance ? "Adding…" : "Add instance"}
                  </Button>
                  {callbackUrl && (
                    <p className="callback-hint">
                      Add this redirect URI to your GitLab OAuth application: {callbackUrl}
                    </p>
                  )}
                </form>
              )}
              {instanceError && <p className="form-error">{instanceError}</p>}
            </Card>
          </div>
        ) : (
          <StateRow
            detail="Select or create a workspace to manage its settings."
            label="No workspace selected"
            tone="empty"
          />
        )}
      </section>
    </WorkspaceFrame>
  );
}
