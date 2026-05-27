import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import type {
  CapabilityFinding,
  CapabilityScore,
  DimensionAnalysis,
} from "../api/analyze";
import { oauthLoginUrl } from "../api/auth";
import {
  analyzeRepository,
  connectRepository,
  disconnectRepository,
  getRepositoryAnalysis,
  getRepositoryAnalysisDelta,
  listGitHubRepositories,
  listRepositoryConnections,
  type GitHubRepository,
  type RepositoryAnalysis,
  type RepositoryAnalysisDelta,
  type RepositoryAnalysisSummary,
  type RepositoryConnection,
} from "../api/repositories";
import {
  AppFrame,
  Badge,
  Button,
  Card,
  Eyebrow,
  StateRow,
  TextInput,
} from "../components";
import { useAuth } from "../lib/auth";
import {
  capabilityMeta,
  dimensionMeta,
  findingTypeMeta,
  formatProvider,
  formatScore,
  ruleMeta,
  statusMeta,
  statusBadgeClassName,
} from "../lib/analyzer";

type GitHubAccessState = "connected" | "needs-reconnect" | "unknown";

export function Dashboard() {
  const { user, loading } = useAuth();
  const [repository, setRepository] = useState("");
  const [connections, setConnections] = useState<RepositoryConnection[]>([]);
  const [githubRepositories, setGitHubRepositories] = useState<
    GitHubRepository[]
  >([]);
  const [connectionsLoading, setConnectionsLoading] = useState(false);
  const [githubLoading, setGitHubLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [githubFilter, setGithubFilter] = useState("");
  const [connectDialogOpen, setConnectDialogOpen] = useState(false);
  const [githubAccess, setGithubAccess] =
    useState<GitHubAccessState>("unknown");
  const [selectedRepositoryId, setSelectedRepositoryId] = useState<
    string | null
  >(null);
  const [analysisByRepository, setAnalysisByRepository] = useState<
    Record<string, RepositoryAnalysis>
  >({});
  const [deltaByRepository, setDeltaByRepository] = useState<
    Record<string, RepositoryAnalysisDelta>
  >({});
  const [analyzingId, setAnalyzingId] = useState<string | null>(null);
  const [loadingAnalysisId, setLoadingAnalysisId] = useState<string | null>(
    null,
  );
  const [analysisErrorByRepository, setAnalysisErrorByRepository] = useState<
    Record<string, string>
  >({});

  useEffect(() => {
    if (!user) {
      setConnections([]);
      setGitHubRepositories([]);
      setSelectedRepositoryId(null);
      setConnectDialogOpen(false);
      setAnalysisByRepository({});
      setDeltaByRepository({});
      setLoadingAnalysisId(null);
      setAnalysisErrorByRepository({});
      return;
    }

    let mounted = true;
    setConnectionsLoading(true);
    setError(null);
    listRepositoryConnections()
      .then((items) => {
        if (mounted) {
          setConnections(items);
        }
      })
      .catch((err: unknown) => {
        if (mounted) {
          setError(
            err instanceof Error
              ? err.message
              : "Could not load connected projects.",
          );
        }
      })
      .finally(() => {
        if (mounted) {
          setConnectionsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [user]);

  useEffect(() => {
    if (connections.length === 0) {
      if (selectedRepositoryId) {
        setSelectedRepositoryId(null);
      }
      return;
    }

    if (
      !selectedRepositoryId ||
      !connections.some((connection) => connection.id === selectedRepositoryId)
    ) {
      setSelectedRepositoryId(connections[0].id);
    }
  }, [connections, selectedRepositoryId]);

  const connectedCount = connections.length;
  const needsGitHubReconnect =
    error?.toLowerCase().includes("reconnect with github") ?? false;

  const filteredConnections = useMemo(() => {
    if (!filter.trim()) return connections;
    const query = filter.trim().toLowerCase();
    return connections.filter((connection) =>
      `${connection.owner}/${connection.name}`.toLowerCase().includes(query),
    );
  }, [connections, filter]);

  const selectedConnection = useMemo(
    () =>
      connections.find(
        (connection) => connection.id === selectedRepositoryId,
      ) ?? null,
    [connections, selectedRepositoryId],
  );
  const selectedAnalysis = selectedConnection
    ? (analysisByRepository[selectedConnection.id] ?? null)
    : null;
  const selectedDelta = selectedConnection
    ? (deltaByRepository[selectedConnection.id] ?? null)
    : null;
  const selectedAnalysisError = selectedConnection
    ? (analysisErrorByRepository[selectedConnection.id] ?? null)
    : null;
  const analyzedCount = connections.filter(
    (connection) =>
      connection.analysisSummary || analysisByRepository[connection.id],
  ).length;
  const selectedConnectionId = selectedConnection?.id ?? null;
  const selectedSummaryRunId = selectedConnection?.analysisSummary?.runId ?? null;
  const hasSelectedAnalysis = selectedConnectionId
    ? Boolean(analysisByRepository[selectedConnectionId])
    : false;
  const hasSelectedDelta = selectedConnectionId
    ? Boolean(deltaByRepository[selectedConnectionId])
    : false;

  useEffect(() => {
    if (!selectedConnectionId || !selectedSummaryRunId || hasSelectedAnalysis) {
      return undefined;
    }

    let mounted = true;
    setLoadingAnalysisId(selectedConnectionId);
    setAnalysisErrorByRepository((current) => {
      const next = { ...current };
      delete next[selectedConnectionId];
      return next;
    });

    getRepositoryAnalysis(selectedConnectionId)
      .then((storedAnalysis) => {
        if (mounted) {
          setAnalysisByRepository((current) => ({
            ...current,
            [selectedConnectionId]: storedAnalysis,
          }));
        }
      })
      .catch((err: unknown) => {
        if (mounted) {
          setAnalysisErrorByRepository((current) => ({
            ...current,
            [selectedConnectionId]:
              err instanceof Error
                ? err.message
                : "Could not load repository analysis.",
          }));
        }
      })
      .finally(() => {
        if (mounted) {
          setLoadingAnalysisId((current) =>
            current === selectedConnectionId ? null : current,
          );
        }
      });

    return () => {
      mounted = false;
    };
  }, [hasSelectedAnalysis, selectedConnectionId, selectedSummaryRunId]);

  useEffect(() => {
    if (
      !selectedConnectionId ||
      !selectedSummaryRunId ||
      !hasSelectedAnalysis ||
      hasSelectedDelta
    ) {
      return undefined;
    }

    let mounted = true;
    getRepositoryAnalysisDelta(selectedConnectionId)
      .then((storedDelta) => {
        if (mounted) {
          setDeltaByRepository((current) => ({
            ...current,
            [selectedConnectionId]: storedDelta,
          }));
        }
      })
      .catch(() => {
        if (mounted) {
          setDeltaByRepository((current) => {
            const next = { ...current };
            delete next[selectedConnectionId];
            return next;
          });
        }
      });

    return () => {
      mounted = false;
    };
  }, [
    hasSelectedAnalysis,
    hasSelectedDelta,
    selectedConnectionId,
    selectedSummaryRunId,
  ]);

  useEffect(() => {
    if (
      !connectDialogOpen ||
      !user ||
      githubLoading ||
      githubRepositories.length > 0 ||
      githubAccess === "needs-reconnect"
    ) {
      return;
    }

    void handleFetchGitHubRepositories();
  }, [
    connectDialogOpen,
    githubAccess,
    githubLoading,
    githubRepositories.length,
    user,
  ]);

  const accessState: GitHubAccessState = needsGitHubReconnect
    ? "needs-reconnect"
    : githubAccess;
  const githubAccessLabel =
    accessState === "needs-reconnect"
      ? "Reconnect needed"
      : accessState === "connected"
        ? "Connected"
        : "Not checked";
  const githubAccessDot =
    accessState === "needs-reconnect"
      ? "error"
      : accessState === "connected"
        ? "success"
        : "warn";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!repository.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const connection = await connectRepository(repository);
      setConnections((current) => [
        connection,
        ...current.filter((item) => item.id !== connection.id),
      ]);
      setSelectedRepositoryId(connection.id);
      setRepository("");
      setConnectDialogOpen(false);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not connect repository.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDisconnect(id: string) {
    setError(null);
    setAnalysisErrorByRepository((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
    setAnalysisByRepository((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
    setDeltaByRepository((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
    try {
      await disconnectRepository(id);
      setConnections((current) => current.filter((item) => item.id !== id));
      setSelectedRepositoryId((current) => (current === id ? null : current));
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not remove repository.",
      );
    }
  }

  async function handleAnalyzeRepository(connection: RepositoryConnection) {
    setSelectedRepositoryId(connection.id);
    setAnalyzingId(connection.id);
    setAnalysisErrorByRepository((current) => {
      const next = { ...current };
      delete next[connection.id];
      return next;
    });
    try {
      const nextAnalysis = await analyzeRepository(connection.id);
      setAnalysisByRepository((current) => ({
        ...current,
        [connection.id]: nextAnalysis,
      }));
      setConnections((current) =>
        current.map((item) =>
          item.id === connection.id
            ? {
                ...item,
                analysisRunCount: Math.max(
                  (item.analysisRunCount ?? 0) + 1,
                  nextAnalysis.runNumber,
                ),
                analysisSummary: summaryFromAnalysis(nextAnalysis),
              }
            : item,
        ),
      );
      try {
        const nextDelta = await getRepositoryAnalysisDelta(connection.id);
        setDeltaByRepository((current) => ({
          ...current,
          [connection.id]: nextDelta,
        }));
      } catch {
        setDeltaByRepository((current) => {
          const next = { ...current };
          delete next[connection.id];
          return next;
        });
      }
    } catch (err) {
      setAnalysisErrorByRepository((current) => ({
        ...current,
        [connection.id]:
          err instanceof Error ? err.message : "Could not analyze repository.",
      }));
    } finally {
      setAnalyzingId(null);
    }
  }

  async function handleFetchGitHubRepositories() {
    setGitHubLoading(true);
    setError(null);
    try {
      setGitHubRepositories(await listGitHubRepositories());
      setGithubAccess("connected");
    } catch (err) {
      setGithubAccess("needs-reconnect");
      setError(
        err instanceof Error
          ? err.message
          : "Could not fetch GitHub repositories.",
      );
    } finally {
      setGitHubLoading(false);
    }
  }

  async function handleConnectGitHubRepository(repo: GitHubRepository) {
    setSubmitting(true);
    setError(null);
    try {
      const connection = await connectRepository(repo.fullName);
      setConnections((current) => [
        connection,
        ...current.filter((item) => item.id !== connection.id),
      ]);
      setSelectedRepositoryId(connection.id);
      setConnectDialogOpen(false);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not connect repository.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <AppFrame>
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Checking session</h2>
            <p>
              Verifying the current browser session before loading the
              workspace.
            </p>
          </Card>
        </section>
      </AppFrame>
    );
  }

  if (!user) {
    return (
      <AppFrame>
        <section className="dashboard-signin">
          <Card className="dashboard-signin__card">
            <Eyebrow>Workspace</Eyebrow>
            <h2>Sign in to view your projects</h2>
            <p>
              Connect a GitHub account to discover repositories and queue them
              for analysis.
            </p>
            <div className="dashboard-signin__actions">
              <a className="button button--primary" href={oauthLoginUrl.github}>
                Continue with GitHub
              </a>
              <a
                className="button button--secondary"
                href={oauthLoginUrl.google}
              >
                Continue with Google
              </a>
            </div>
          </Card>
        </section>
      </AppFrame>
    );
  }

  return (
    <AppFrame>
      <section className="dashboard-band" aria-labelledby="dashboard-title">
        <header className="dashboard-header">
          <div className="dashboard-header__copy">
            <Eyebrow>Workspace</Eyebrow>
            <h2 id="dashboard-title">Connected projects</h2>
            <p>
              Manage the GitHub repositories Scaffy can analyze. Connect new
              projects, audit access, and queue them for the pipeline grader.
            </p>
          </div>
          <div className="dashboard-header__actions">
            <Button
              disabled={githubLoading}
              onClick={() => {
                setConnectDialogOpen(true);
                void handleFetchGitHubRepositories();
              }}
              variant="secondary"
            >
              {githubLoading ? "Syncing" : "Refresh GitHub"}
            </Button>
            <Button onClick={() => setConnectDialogOpen(true)}>
              Connect repository
            </Button>
          </div>
        </header>

        <div
          className="dashboard-summary"
          aria-label="Repository workspace status"
        >
          <div className="dashboard-summary__item">
            <span>Connected</span>
            <strong>{connectedCount}</strong>
          </div>
          <div className="dashboard-summary__item">
            <span>GitHub access</span>
            <strong>
              <span
                aria-hidden="true"
                className={`dot dot--${githubAccessDot}`}
              />
              {githubAccessLabel}
            </strong>
          </div>
          <div className="dashboard-summary__item">
            <span>Fetched</span>
            <strong>{githubRepositories.length}</strong>
          </div>
          <div className="dashboard-summary__item dashboard-summary__item--wide">
            <span>Next step</span>
            <strong>
              {connectedCount === 0
                ? "Connect a repository"
                : analyzedCount === connectedCount
                  ? "Review findings"
                  : "Analyze selected project"}
            </strong>
          </div>
        </div>

        <div className="dashboard-layout">
          <ProjectSidebar
            analysisByRepository={analysisByRepository}
            connections={connections}
            filter={filter}
            filteredConnections={filteredConnections}
            loading={connectionsLoading}
            onFilterChange={setFilter}
            onSelect={setSelectedRepositoryId}
            selectedRepositoryId={selectedRepositoryId}
          />

          <div className="project-workspace">
            <ProjectDetail
              analysis={selectedAnalysis}
              connection={selectedConnection}
              delta={selectedDelta}
              error={selectedAnalysisError}
              loading={
                selectedConnection
                  ? analyzingId === selectedConnection.id
                  : false
              }
              loadingStored={
                selectedConnection
                  ? loadingAnalysisId === selectedConnection.id
                  : false
              }
              onAnalyze={handleAnalyzeRepository}
              onConnect={() => setConnectDialogOpen(true)}
              onDisconnect={handleDisconnect}
            />

          </div>
        </div>

        <RepositoryConnectDialog
          connections={connections}
          error={error}
          githubFilter={githubFilter}
          githubLoading={githubLoading}
          githubRepositories={githubRepositories}
          needsGitHubReconnect={needsGitHubReconnect}
          onConnectGithubRepository={handleConnectGitHubRepository}
          onFetchGithubRepositories={handleFetchGitHubRepositories}
          onGithubFilterChange={setGithubFilter}
          onOpenChange={setConnectDialogOpen}
          onRepositoryChange={setRepository}
          onSubmit={handleSubmit}
          open={connectDialogOpen}
          repository={repository}
          submitting={submitting}
        />
      </section>
    </AppFrame>
  );
}

type ProjectSidebarProps = Readonly<{
  analysisByRepository: Record<string, RepositoryAnalysis>;
  connections: RepositoryConnection[];
  filter: string;
  filteredConnections: RepositoryConnection[];
  loading: boolean;
  onFilterChange: (value: string) => void;
  onSelect: (id: string) => void;
  selectedRepositoryId: string | null;
}>;

function ProjectSidebar({
  analysisByRepository,
  connections,
  filter,
  filteredConnections,
  loading,
  onFilterChange,
  onSelect,
  selectedRepositoryId,
}: ProjectSidebarProps) {
  return (
    <Card
      as="section"
      className="project-sidebar"
      aria-label="Connected projects"
    >
      <div className="project-sidebar__header">
        <div>
          <Eyebrow>Projects</Eyebrow>
          <h3>{connections.length} connected</h3>
        </div>
        <SearchInput
          onChange={onFilterChange}
          placeholder="Filter projects"
          value={filter}
        />
      </div>

      {loading ? (
        <StateRow
          detail="Loading repositories from /api/repositories."
          label="Loading connected projects"
          tone="loading"
        />
      ) : connections.length === 0 ? (
        <div className="empty-state empty-state--sidebar">
          <h4>No projects yet</h4>
          <p>
            Connect a GitHub repository to start building an analysis history.
          </p>
        </div>
      ) : filteredConnections.length === 0 ? (
        <div className="empty-state empty-state--sidebar">
          <h4>No matches</h4>
          <p>No connected project matches “{filter}”.</p>
        </div>
      ) : (
        <ul className="project-list">
          {filteredConnections.map((connection) => {
            const selected = selectedRepositoryId === connection.id;
            const summary =
              connection.analysisSummary ||
              (analysisByRepository[connection.id]
                ? summaryFromAnalysis(analysisByRepository[connection.id])
                : null);
            return (
              <li key={connection.id}>
                <button
                  className={`project-list__button${selected ? " project-list__button--active" : ""}`}
                  onClick={() => onSelect(connection.id)}
                  type="button"
                >
                  <span aria-hidden="true" className="repo-cell__avatar">
                    {connection.owner.charAt(0).toUpperCase()}
                  </span>
                  <span className="project-list__copy">
                    <strong>
                      {connection.owner}/{connection.name}
                    </strong>
                    <span>
                      {summary
                        ? `${formatScore(summary.overallScore)} · ${connection.analysisRunCount || summary.runNumber} ${
                            (connection.analysisRunCount || summary.runNumber) ===
                            1
                              ? "run"
                              : "runs"
                          } · ${formatRelative(summary.analyzedAt)}`
                        : `Connected ${formatRelative(connection.connectedAt)}`}
                    </span>
                  </span>
                  <span className="project-list__status">
                    {summary ? "Analyzed" : "Ready"}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

type ProjectDetailProps = Readonly<{
  analysis: RepositoryAnalysis | null;
  connection: RepositoryConnection | null;
  delta: RepositoryAnalysisDelta | null;
  error: string | null;
  loading: boolean;
  loadingStored: boolean;
  onAnalyze: (connection: RepositoryConnection) => void;
  onConnect: () => void;
  onDisconnect: (id: string) => void;
}>;

function ProjectDetail({
  analysis,
  connection,
  delta,
  error,
  loading,
  loadingStored,
  onAnalyze,
  onConnect,
  onDisconnect,
}: ProjectDetailProps) {
  if (!connection) {
    return (
      <Card as="section" className="project-detail project-detail--empty">
        <Eyebrow>Project detail</Eyebrow>
        <h3>Select a repository</h3>
        <p>
          Connected projects appear in the sidebar. Select one to inspect its
          analysis state.
        </p>
        <button className="button button--primary" onClick={onConnect} type="button">
          Connect repository
        </button>
      </Card>
    );
  }

  if (loading || loadingStored) {
    return (
      <Card as="section" className="project-detail">
        <ProjectDetailHeader
          connection={connection}
          hasAnalysis={Boolean(connection.analysisSummary || analysis)}
          loading={loading}
          onAnalyze={onAnalyze}
          onDisconnect={onDisconnect}
        />
        <StateRow
          detail={
            loadingStored
              ? "Loading the saved repository analysis from Scaffy."
              : "Finding .github/workflows files and running the Scaffy capability analyzer."
          }
          label={loadingStored ? "Loading saved analysis" : "Analyzing repository"}
          tone="loading"
        />
      </Card>
    );
  }

  return (
    <Card as="section" className="project-detail">
      <ProjectDetailHeader
        connection={connection}
        hasAnalysis={Boolean(connection.analysisSummary || analysis)}
        loading={loading}
        onAnalyze={onAnalyze}
        onDisconnect={onDisconnect}
      />

      {error ? (
        <div className="analysis-empty analysis-empty--error">
          <StateRow
            detail={error}
            icon="!"
            label="Repository analysis failed"
            tone="error"
          />
          {error.toLowerCase().includes("github") && (
            <a
              className="button button--secondary button--small"
              href={oauthLoginUrl.github}
            >
              Reconnect GitHub
            </a>
          )}
        </div>
      ) : analysis ? (
        <AnalysisBreakdown analysis={analysis} delta={delta} />
      ) : (
        <div className="analysis-empty">
          <div>
            <Eyebrow>Analysis</Eyebrow>
            <h3>No analysis has been started</h3>
            <p>
              Scaffy will inspect the repository, detect GitHub Actions workflow
              files, and score the pipeline against the capability model.
            </p>
          </div>
          <Button onClick={() => onAnalyze(connection)}>
            Analyze repository
          </Button>
        </div>
      )}
    </Card>
  );
}

type ProjectDetailHeaderProps = Readonly<{
  connection: RepositoryConnection;
  hasAnalysis: boolean;
  loading: boolean;
  onAnalyze: (connection: RepositoryConnection) => void;
  onDisconnect: (id: string) => void;
}>;

function ProjectDetailHeader({
  connection,
  hasAnalysis,
  loading,
  onAnalyze,
  onDisconnect,
}: ProjectDetailHeaderProps) {
  return (
    <header className="project-detail__header">
      <div className="project-detail__title">
        <Eyebrow>{formatProvider(connection.provider)}</Eyebrow>
        <h3>
          {connection.owner}/{connection.name}
        </h3>
        <p>
          Connected {formatRelative(connection.connectedAt)} ·{" "}
          <a href={connection.url} rel="noreferrer" target="_blank">
            Open repository
          </a>
        </p>
      </div>
      <div className="project-detail__actions">
        <Button
          className="button--small"
          disabled={loading}
          onClick={() => onAnalyze(connection)}
          variant="secondary"
        >
          {loading ? "Analyzing" : hasAnalysis ? "Re-analyze" : "Run analysis"}
        </Button>
        <a
          aria-label={`Open ${connection.owner}/${connection.name} on GitHub`}
          className="icon-button"
          href={connection.url}
          rel="noreferrer"
          target="_blank"
          title="Open on GitHub"
        >
          <IconExternal />
        </a>
        <button
          aria-label={`Disconnect ${connection.owner}/${connection.name}`}
          className="icon-button icon-button--danger"
          onClick={() => onDisconnect(connection.id)}
          title="Disconnect repository"
          type="button"
        >
          <IconTrash />
        </button>
      </div>
    </header>
  );
}

type AnalysisBreakdownProps = Readonly<{
  analysis: RepositoryAnalysis;
  delta: RepositoryAnalysisDelta | null;
}>;

function AnalysisBreakdown({ analysis, delta }: AnalysisBreakdownProps) {
  const findings = flattenAnalysisFindings(analysis.analysis.dimensions);
  const openFindings = findings.filter(
    (finding) => finding.finding.type !== "POSITIVE",
  );
  const passedFindings = findings.filter(
    (finding) => finding.finding.type === "POSITIVE",
  );
  const smells = findings.filter((finding) => finding.finding.type === "SMELL");
  const missing = findings.filter(
    (finding) => finding.finding.type === "MISSING",
  );

  return (
    <div className="analysis-breakdown">
      <div className="analysis-panel__header">
        <div>
          <Eyebrow>Analysis result</Eyebrow>
          <h3>{analysis.repository}</h3>
          <p>
            {formatProvider(analysis.analysis.provider)} ·{" "}
            Run {analysis.runNumber} · <code>{analysis.workflowPath}</code>
          </p>
        </div>
        <div className="analysis-score">
          <strong>{formatScore(analysis.analysis.overallScore)}</strong>
          <span>Level {analysis.analysis.overallLevel}</span>
        </div>
      </div>

      <div className="analysis-meta">
        <Badge>{formatProvider(analysis.analysis.provider)}</Badge>
        <Badge
          className={statusBadgeClassName(analysis.analysis.overallStatus)}
        >
          {statusMeta(analysis.analysis.overallStatus).label}
        </Badge>
        <span>
          {openFindings.length} open{" "}
          {openFindings.length === 1 ? "issue" : "issues"}
        </span>
        <span>Analyzed {formatRelative(analysis.analyzedAt)}</span>
      </div>

      <AnalysisDeltaPanel delta={delta} />

      <div className="scanner-summary" aria-label="Analysis finding summary">
        <div>
          <span>Open issues</span>
          <strong>{openFindings.length}</strong>
        </div>
        <div>
          <span>Missing controls</span>
          <strong>{missing.length}</strong>
        </div>
        <div>
          <span>Smells</span>
          <strong>{smells.length}</strong>
        </div>
        <div>
          <span>Detected checks</span>
          <strong>{passedFindings.length}</strong>
        </div>
      </div>

      <QualityAreaTable
        dimensions={analysis.analysis.dimensions}
        findings={findings}
      />
      <FindingsTable findings={findings} />
    </div>
  );
}

type AnalysisDeltaPanelProps = Readonly<{
  delta: RepositoryAnalysisDelta | null;
}>;

function AnalysisDeltaPanel({ delta }: AnalysisDeltaPanelProps) {
  if (!delta) {
    return (
      <section className="analysis-delta analysis-delta--empty">
        <Eyebrow>Delta</Eyebrow>
        <h4>Loading comparison</h4>
        <p>Scaffy is preparing the latest run comparison.</p>
      </section>
    );
  }

  if (!delta.hasPrevious || !delta.overall || !delta.baseRun) {
    return (
      <section className="analysis-delta analysis-delta--empty">
        <Eyebrow>Delta</Eyebrow>
        <h4>No previous analysis yet</h4>
        <p>
          Run the analyzer again later to compare this snapshot against the next
          one.
        </p>
      </section>
    );
  }

  const changedFindings = delta.findingChanges.filter(
    (finding) => finding.kind !== "unchanged",
  );
  const improved = changedFindings.filter(
    (finding) => finding.direction === "improved",
  ).length;
  const worsened = changedFindings.filter(
    (finding) => finding.direction === "worsened",
  ).length;
  const topDimensions = delta.dimensions
    .filter((dimension) => dimension.direction !== "unchanged")
    .slice(0, 4);
  const topFindings = changedFindings.slice(0, 6);

  return (
    <section className="analysis-delta">
      <header className="analysis-delta__header">
        <div>
          <Eyebrow>Latest delta</Eyebrow>
          <h4>
            Run {delta.currentRun.runNumber} vs run {delta.baseRun.runNumber}
          </h4>
          <p>
            Compared against {formatRelative(delta.baseRun.analyzedAt)}.
          </p>
        </div>
        <Badge className={`delta-badge delta-badge--${delta.overall.direction}`}>
          {formatDeltaDirection(delta.overall.direction)}
        </Badge>
      </header>

      <div className="analysis-delta__metrics">
        <div>
          <span>Score</span>
          <strong>{formatSignedNumber(delta.overall.scoreDelta)}</strong>
        </div>
        <div>
          <span>Level</span>
          <strong>{formatSignedInteger(delta.overall.levelDelta)}</strong>
        </div>
        <div>
          <span>Improved</span>
          <strong>{improved}</strong>
        </div>
        <div>
          <span>Worsened</span>
          <strong>{worsened}</strong>
        </div>
      </div>

      {topDimensions.length > 0 && (
        <div className="analysis-delta__section">
          <strong>Dimension movement</strong>
          <ul>
            {topDimensions.map((dimension) => (
              <li key={dimension.dimension}>
                <span>{dimensionMeta(dimension.dimension).label}</span>
                <Badge
                  className={`delta-badge delta-badge--${dimension.direction}`}
                >
                  {formatSignedNumber(dimension.scoreDelta)}
                </Badge>
              </li>
            ))}
          </ul>
        </div>
      )}

      {topFindings.length > 0 ? (
        <div className="analysis-delta__section">
          <strong>Finding changes</strong>
          <ul>
            {topFindings.map((finding) => (
              <li
                key={`${finding.kind}-${finding.type}-${finding.dimension}-${finding.capability}-${finding.ruleId}`}
              >
                <span>
                  {formatFindingChangeKind(finding.kind)}{" "}
                  {ruleMeta(finding.ruleId).label}
                </span>
                <Badge
                  className={`delta-badge delta-badge--${finding.direction}`}
                >
                  {formatDeltaDirection(finding.direction)}
                </Badge>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="analysis-delta__quiet">
          No rule-level findings changed between the last two runs.
        </p>
      )}
    </section>
  );
}

type FlattenedAnalysisFinding = Readonly<{
  capability: CapabilityScore;
  capabilityDescription: string;
  capabilityLabel: string;
  dimension: DimensionAnalysis;
  dimensionDescription: string;
  dimensionLabel: string;
  finding: CapabilityFinding;
  ruleDescription: string;
  ruleLabel: string;
  typeDescription: string;
  typeLabel: string;
}>;

type QualityAreaTableProps = Readonly<{
  dimensions: DimensionAnalysis[];
  findings: FlattenedAnalysisFinding[];
}>;

function QualityAreaTable({ dimensions, findings }: QualityAreaTableProps) {
  return (
    <section className="quality-area-panel">
      <header className="scanner-section-header">
        <div>
          <Eyebrow>Quality areas</Eyebrow>
          <h4>Capability coverage</h4>
        </div>
        <span>{dimensions.length} areas</span>
      </header>
      <div className="quality-area-table">
        {dimensions.map((dimension) => {
          const info = dimensionMeta(dimension.dimension);
          const areaFindings = findings.filter(
            (finding) => finding.dimension.dimension === dimension.dimension,
          );
          const open = areaFindings.filter(
            (finding) => finding.finding.type !== "POSITIVE",
          ).length;
          const passed = areaFindings.length - open;
          const status = statusMeta(dimension.status);
          return (
            <article className="quality-area-row" key={dimension.dimension}>
              <div className="quality-area-row__main">
                <strong>{info.label}</strong>
                <span>{info.description}</span>
              </div>
              <div className="quality-area-row__score">
                <strong>
                  {dimension.status === "not_evaluated"
                    ? "—"
                    : formatScore(dimension.score)}
                </strong>
                <span>
                  {dimension.status === "not_evaluated"
                    ? status.label
                    : `Level ${dimension.level}`}
                </span>
              </div>
              <div className="quality-area-row__counts">
                <span>{open} open</span>
                <span>{passed} detected</span>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

type FindingFilter = "open" | "passed" | "all";

type FindingsTableProps = Readonly<{
  findings: FlattenedAnalysisFinding[];
}>;

function FindingsTable({ findings }: FindingsTableProps) {
  const [filter, setFilter] = useState<FindingFilter>("open");
  const filteredFindings = findings.filter((finding) => {
    if (filter === "open") {
      return finding.finding.type !== "POSITIVE";
    }
    if (filter === "passed") {
      return finding.finding.type === "POSITIVE";
    }
    return true;
  });
  const openCount = findings.filter(
    (finding) => finding.finding.type !== "POSITIVE",
  ).length;
  const passedCount = findings.length - openCount;

  return (
    <section className="findings-panel">
      <header className="scanner-section-header">
        <div>
          <Eyebrow>Findings</Eyebrow>
          <h4>Issue list</h4>
        </div>
        <div className="findings-filter" aria-label="Finding filter">
          <button
            className={filter === "open" ? "findings-filter__item--active" : ""}
            onClick={() => setFilter("open")}
            type="button"
          >
            Open {openCount}
          </button>
          <button
            className={
              filter === "passed" ? "findings-filter__item--active" : ""
            }
            onClick={() => setFilter("passed")}
            type="button"
          >
            Detected {passedCount}
          </button>
          <button
            className={filter === "all" ? "findings-filter__item--active" : ""}
            onClick={() => setFilter("all")}
            type="button"
          >
            All {findings.length}
          </button>
        </div>
      </header>

      {filteredFindings.length === 0 ? (
        <div className="findings-empty">
          <h4>No findings in this view</h4>
          <p>Switch filters to inspect detected checks or all analyzer output.</p>
        </div>
      ) : (
        <div className="findings-list" aria-label="Findings">
          {filteredFindings.map((finding) => (
            <FindingRow
              finding={finding}
              key={`${finding.finding.ruleId}-${finding.finding.dimension}-${finding.finding.capability}-${finding.finding.type}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}

type FindingRowProps = Readonly<{
  finding: FlattenedAnalysisFinding;
}>;

function FindingRow({ finding }: FindingRowProps) {
  return (
    <details
      className={`finding-row finding-row--${finding.finding.type.toLowerCase()}`}
    >
      <summary className="finding-row__summary">
        <span className="finding-row__main">
          <span className="finding-row__heading">
            <Badge
              className={`finding-badge finding-badge--${finding.finding.type.toLowerCase()}`}
              title={finding.typeDescription}
            >
              {finding.typeLabel}
            </Badge>
            <strong>{finding.ruleLabel}</strong>
          </span>
          <span className="finding-row__description">
            {finding.ruleDescription}
          </span>
          <span className="finding-row__meta">
            <span>{finding.dimensionLabel}</span>
            <span>{finding.capabilityLabel}</span>
            {finding.finding.location ? (
              <code>{finding.finding.location}</code>
            ) : (
              <span>No location</span>
            )}
          </span>
        </span>
      </summary>
      <div className="finding-row__details">
        <div>
          <strong>Area</strong>
          <p>{finding.dimensionDescription}</p>
          <p>{finding.capabilityDescription}</p>
        </div>
        <div>
          <strong>Evidence</strong>
          <p>{finding.finding.evidence ?? "No evidence was emitted."}</p>
          {finding.finding.location && <code>{finding.finding.location}</code>}
        </div>
      </div>
    </details>
  );
}

type RepositoryConnectDialogProps = Readonly<{
  connections: RepositoryConnection[];
  error: string | null;
  githubFilter: string;
  githubLoading: boolean;
  githubRepositories: GitHubRepository[];
  needsGitHubReconnect: boolean;
  onConnectGithubRepository: (repo: GitHubRepository) => void;
  onFetchGithubRepositories: () => void;
  onGithubFilterChange: (value: string) => void;
  onOpenChange: (open: boolean) => void;
  onRepositoryChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  open: boolean;
  repository: string;
  submitting: boolean;
}>;

function RepositoryConnectDialog({
  connections,
  error,
  githubFilter,
  githubLoading,
  githubRepositories,
  needsGitHubReconnect,
  onConnectGithubRepository,
  onFetchGithubRepositories,
  onGithubFilterChange,
  onOpenChange,
  onRepositoryChange,
  onSubmit,
  open,
  repository,
  submitting,
}: RepositoryConnectDialogProps) {
  const filteredGithubRepositories = useMemo(() => {
    if (!githubFilter.trim()) return githubRepositories;
    const query = githubFilter.trim().toLowerCase();
    return githubRepositories.filter((repo) =>
      repo.fullName.toLowerCase().includes(query),
    );
  }, [githubFilter, githubRepositories]);

  return (
    <Dialog.Root onOpenChange={onOpenChange} open={open}>
      <Dialog.Portal>
        <Dialog.Overlay className="repository-dialog__overlay" />
        <Dialog.Content className="repository-dialog">
          <header className="repository-dialog__header">
            <div>
              <Eyebrow>Add project</Eyebrow>
              <Dialog.Title className="repository-dialog__title">
                Choose a GitHub repository
              </Dialog.Title>
              <Dialog.Description className="repository-dialog__description">
                Select a repository from your GitHub account, or paste a
                repository URL manually.
              </Dialog.Description>
            </div>
            <Dialog.Close className="icon-button" aria-label="Close dialog">
              <IconClose />
            </Dialog.Close>
          </header>

          <div className="repository-dialog__toolbar">
            <SearchInput
              onChange={onGithubFilterChange}
              placeholder="Search repositories"
              value={githubFilter}
            />
            <Button
              className="button--small"
              disabled={githubLoading}
              onClick={onFetchGithubRepositories}
              variant="secondary"
            >
              {githubLoading
                ? "Refreshing"
                : githubRepositories.length === 0
                  ? "Fetch GitHub"
                  : "Refresh"}
            </Button>
          </div>

          <div className="repository-dialog__body">
            {githubLoading ? (
              <StateRow
                detail="Fetching repositories from your connected GitHub account."
                label="Loading GitHub repositories"
                tone="loading"
              />
            ) : needsGitHubReconnect ? (
              <div className="repository-dialog__empty">
                <h4>Reconnect GitHub</h4>
                <p>
                  Scaffy needs a fresh GitHub authorization before it can list
                  your repositories.
                </p>
                <a
                  className="button button--secondary button--small"
                  href={oauthLoginUrl.github}
                >
                  Reconnect GitHub
                </a>
              </div>
            ) : githubRepositories.length === 0 ? (
              <div className="repository-dialog__empty">
                <h4>No repositories loaded</h4>
                <p>
                  Fetch your GitHub repositories to choose one without pasting a
                  link.
                </p>
                <Button onClick={onFetchGithubRepositories}>
                  Fetch GitHub repositories
                </Button>
              </div>
            ) : filteredGithubRepositories.length === 0 ? (
              <div className="repository-dialog__empty">
                <h4>No matches</h4>
                <p>No repository matches “{githubFilter}”.</p>
              </div>
            ) : (
              <ul className="repository-picker-list">
                {filteredGithubRepositories.map((repo) => {
                  const connected = connections.some(
                    (connection) =>
                      `${connection.owner}/${connection.name}`.toLowerCase() ===
                      repo.fullName.toLowerCase(),
                  );
                  return (
                    <li className="repository-picker-list__item" key={repo.fullName}>
                      <button
                        className="repository-picker-list__main"
                        disabled={connected || submitting}
                        onClick={() => onConnectGithubRepository(repo)}
                        type="button"
                      >
                        <span className="repository-picker-list__name">
                          {repo.fullName}
                        </span>
                        <span className="repository-picker-list__meta">
                          <span aria-hidden="true" className="dot" />
                          {repo.privateRepository ? "Private" : "Public"}
                        </span>
                      </button>
                      <Button
                        className="button--small"
                        disabled={connected || submitting}
                        onClick={() => onConnectGithubRepository(repo)}
                        variant={connected ? "secondary" : "primary"}
                      >
                        {connected ? "Connected" : "Connect"}
                      </Button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          <form className="repository-dialog__manual" onSubmit={onSubmit}>
            <div>
              <label htmlFor="repository">Paste repository</label>
              <span>owner/repo or https://github.com/owner/repo</span>
            </div>
            <div className="repository-dialog__manual-row">
              <TextInput
                id="repository"
                onChange={(event) => onRepositoryChange(event.target.value)}
                placeholder="owner/repo"
                value={repository}
              />
              <Button disabled={submitting} type="submit">
                {submitting ? "Connecting" : "Connect"}
              </Button>
            </div>
          </form>

          {error && (
            <div className="repository-dialog__error">
              <p className="form-error">{error}</p>
              {needsGitHubReconnect && (
                <a
                  className="button button--secondary button--small"
                  href={oauthLoginUrl.github}
                >
                  Reconnect GitHub
                </a>
              )}
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

type SearchInputProps = Readonly<{
  onChange: (value: string) => void;
  placeholder: string;
  value: string;
}>;

function SearchInput({ onChange, placeholder, value }: SearchInputProps) {
  return (
    <label className="search-input">
      <IconSearch />
      <input
        aria-label={placeholder}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type="search"
        value={value}
      />
    </label>
  );
}

function summaryFromAnalysis(
  analysis: RepositoryAnalysis,
): RepositoryAnalysisSummary {
  return {
    runId: analysis.runId,
    runNumber: analysis.runNumber,
    analyzedAt: analysis.analyzedAt,
    workflowPath: analysis.workflowPath,
    workflowContentHash: analysis.workflowContentHash,
    overallScore: analysis.analysis.overallScore,
    overallLevel: analysis.analysis.overallLevel,
    overallStatus: analysis.analysis.overallStatus,
    analysisSchemaVersion: analysis.analysisSchemaVersion,
    analyzerModelVersion: analysis.analyzerModelVersion,
  };
}

function flattenAnalysisFindings(
  dimensions: DimensionAnalysis[],
): FlattenedAnalysisFinding[] {
  return dimensions.flatMap((dimension) => {
    const dimensionInfo = dimensionMeta(dimension.dimension);
    return dimension.capabilityScores.flatMap((capability) => {
      const capabilityInfo = capabilityMeta(capability.capability);
      return capability.findings.map((finding) => {
        const rule = ruleMeta(finding.ruleId);
        const type = findingTypeMeta(finding.type);
        return {
          capability,
          capabilityDescription: capabilityInfo.description,
          capabilityLabel: capabilityInfo.label,
          dimension,
          dimensionDescription: dimensionInfo.description,
          dimensionLabel: dimensionInfo.label,
          finding,
          ruleDescription: rule.description,
          ruleLabel: rule.label,
          typeDescription: type.description,
          typeLabel: type.label,
        };
      });
    });
  });
}

function formatSignedNumber(value: number): string {
  if (Math.abs(value) < 0.005) {
    return "0.00";
  }
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}`;
}

function formatSignedInteger(value: number): string {
  if (value === 0) {
    return "0";
  }
  return `${value > 0 ? "+" : ""}${value}`;
}

function formatDeltaDirection(direction: string): string {
  switch (direction) {
    case "improved":
      return "Improved";
    case "worsened":
      return "Worsened";
    case "mixed":
      return "Mixed";
    default:
      return "Unchanged";
  }
}

function formatFindingChangeKind(kind: string): string {
  switch (kind) {
    case "added":
      return "Added";
    case "removed":
      return "Removed";
    default:
      return "Kept";
  }
}

function IconSearch() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <circle cx="7" cy="7" r="5" />
      <path d="m11 11 3.5 3.5" strokeLinecap="round" />
    </svg>
  );
}

function IconExternal() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <path d="M9 3h4v4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M13 3 7.5 8.5" strokeLinecap="round" />
      <path
        d="M12.5 9.5V12a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 12V5A1.5 1.5 0 0 1 4 3.5h2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function IconClose() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <path d="M4 4l8 8" strokeLinecap="round" />
      <path d="M12 4l-8 8" strokeLinecap="round" />
    </svg>
  );
}

function IconTrash() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <path d="M3 4.5h10" strokeLinecap="round" />
      <path
        d="M6 4.5V3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1.5"
        strokeLinecap="round"
      />
      <path
        d="M4.5 4.5 5 13a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1l.5-8.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function formatRelative(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  const diffMs = date.getTime() - Date.now();
  const diffSec = Math.round(diffMs / 1000);
  const abs = Math.abs(diffSec);

  if (abs < 60) return "just now";
  const minutes = Math.round(diffSec / 60);
  if (Math.abs(minutes) < 60) return formatChunk(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return formatChunk(hours, "hour");
  const days = Math.round(hours / 24);
  if (Math.abs(days) < 30) return formatChunk(days, "day");
  const months = Math.round(days / 30);
  if (Math.abs(months) < 12) return formatChunk(months, "month");
  const years = Math.round(days / 365);
  return formatChunk(years, "year");
}

function formatChunk(value: number, unit: string): string {
  const n = Math.abs(value);
  const plural = n === 1 ? unit : `${unit}s`;
  return value < 0 ? `${n} ${plural} ago` : `in ${n} ${plural}`;
}
