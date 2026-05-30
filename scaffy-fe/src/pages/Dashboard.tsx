import { useEffect, useMemo, useRef, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Link } from "react-router-dom";
import type * as Monaco from "monaco-editor";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker.js?worker";
import type {
  CapabilityFinding,
  CapabilityScore,
  DimensionAnalysis,
} from "../api/analyze";
import { oauthLoginUrl } from "../api/auth";
import {
  analyzeRepository,
  createRepositoryPublication,
  disconnectRepository,
  getRepositoryPublication,
  getRepositoryAnalysis,
  getRepositoryAnalysisDelta,
  listRepositoryConnections,
  type RepositoryPublication,
  type RepositoryAnalysis,
  type RepositoryAnalysisDelta,
  type RepositoryAnalysisSummary,
  type RepositoryConnection,
} from "../api/repositories";
import {
  Badge,
  Button,
  Card,
  Eyebrow,
  StateRow,
  TextInput,
  WorkspaceFrame,
} from "../components";
import { ConnectRepositoryDialog } from "../components/ConnectRepositoryDialog";
import {
  MaturityPicker,
  StackIcon,
  StackPresetGroup,
  WizardStep,
} from "../components/wizard";
import {
  createInitJob,
  getInitCatalog,
  getInitJob,
  type InitCatalog,
  type InitJob,
  type StackCatalogOption,
} from "../api/init";
import { listConnections } from "../api/auth";
import { useAuth } from "../lib/auth";
import { useWorkspace } from "../lib/workspace";
import {
  capabilityMeta,
  dimensionMeta,
  findingTypeMeta,
  formatMaturityLevel,
  formatProvider,
  formatScore,
  ruleMeta,
  statusMeta,
  statusBadgeClassName,
} from "../lib/analyzer";

const monacoGlobal = globalThis as typeof globalThis & {
  MonacoEnvironment?: { getWorker: () => Worker };
};

monacoGlobal.MonacoEnvironment ??= {
  getWorker: () => new EditorWorker(),
};

export function Dashboard() {
  const { user } = useAuth();
  const { activeWorkspace } = useWorkspace();
  const activeWorkspaceId = activeWorkspace?.id ?? null;
  const [connections, setConnections] = useState<RepositoryConnection[]>([]);
  const [connectionsLoading, setConnectionsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [connectDialogOpen, setConnectDialogOpen] = useState(false);
  const [connectInitialKey, setConnectInitialKey] = useState<string | null>(null);
  const [hasProviderConnections, setHasProviderConnections] = useState<
    boolean | null
  >(null);
  const [creatingProject, setCreatingProject] = useState(false);
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
      setSelectedRepositoryId(null);
      setConnectDialogOpen(false);
      setCreatingProject(false);
      setAnalysisByRepository({});
      setDeltaByRepository({});
      setLoadingAnalysisId(null);
      setAnalysisErrorByRepository({});
      return;
    }

    let mounted = true;
    setConnectionsLoading(true);
    setError(null);
    setSelectedRepositoryId(null);
    setAnalysisByRepository({});
    setDeltaByRepository({});
    setAnalysisErrorByRepository({});
    listConnections()
      .then((items) => {
        if (mounted) {
          setHasProviderConnections(items.length > 0);
        }
      })
      .catch(() => {
        if (mounted) {
          setHasProviderConnections(false);
        }
      });
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
  }, [user, activeWorkspaceId]);

  useEffect(() => {
    if (
      selectedRepositoryId &&
      !connections.some((connection) => connection.id === selectedRepositoryId)
    ) {
      setSelectedRepositoryId(null);
    }
  }, [connections, selectedRepositoryId]);

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
    if (!user) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const connected = params.get("connected");
    if (!connected) {
      return;
    }
    const instance = params.get("instance");
    setConnectInitialKey(
      connected === "gitlab" ? (instance ?? "gitlab.com") : connected,
    );
    setConnectDialogOpen(true);
    const url = new URL(window.location.href);
    url.searchParams.delete("connected");
    url.searchParams.delete("instance");
    window.history.replaceState({}, "", url.toString());
  }, [user]);

  function handleConnected(connection: RepositoryConnection) {
    setConnections((current) => [
      connection,
      ...current.filter((item) => item.id !== connection.id),
    ]);
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

  function handleCreated(
    connection: RepositoryConnection,
    analysis: RepositoryAnalysis | null,
  ) {
    setConnections((current) => [
      connection,
      ...current.filter((item) => item.id !== connection.id),
    ]);
    if (analysis) {
      setAnalysisByRepository((current) => ({
        ...current,
        [connection.id]: analysis,
      }));
      setConnections((current) =>
        current.map((item) =>
          item.id === connection.id
            ? {
                ...item,
                analysisRunCount: Math.max(
                  item.analysisRunCount ?? 0,
                  analysis.runNumber,
                ),
                analysisSummary: summaryFromAnalysis(analysis),
              }
            : item,
        ),
      );
    }
    setCreatingProject(false);
    setSelectedRepositoryId(connection.id);
  }

  let body: React.ReactNode;
  if (creatingProject) {
    body = (
      <CreateProjectPanel
        onCancel={() => setCreatingProject(false)}
        onCreated={handleCreated}
      />
    );
  } else if (selectedConnection) {
    body = (
      <section className="ws-page project-detail-page">
        <button
          className="ws-back"
          onClick={() => setSelectedRepositoryId(null)}
          type="button"
        >
          <IconBack />
          Back to projects
        </button>
        <ProjectDetail
          analysis={selectedAnalysis}
          connection={selectedConnection}
          delta={selectedDelta}
          error={selectedAnalysisError}
          loading={analyzingId === selectedConnection.id}
          loadingStored={loadingAnalysisId === selectedConnection.id}
          onAnalyze={handleAnalyzeRepository}
          onConnect={() => setConnectDialogOpen(true)}
          onCreate={() => setCreatingProject(true)}
          onDisconnect={handleDisconnect}
        />
      </section>
    );
  } else {
    body = (
      <section className="ws-page projects-page" aria-labelledby="projects-title">
        <header className="ws-page__header">
          <div className="ws-page__heading">
            <Eyebrow>{activeWorkspace?.name ?? "Workspace"}</Eyebrow>
            <h2 id="projects-title">Projects</h2>
            <p className="ws-page__status">
              <span>Connect GitHub or GitLab repositories to this workspace.</span>
            </p>
          </div>
          <div className="ws-page__actions">
            <Button
              onClick={() => {
                setConnectInitialKey(null);
                setConnectDialogOpen(true);
              }}
              variant="secondary"
            >
              Connect repository
            </Button>
            <Button onClick={() => setCreatingProject(true)}>Create project</Button>
          </div>
        </header>

        {error && (
          <StateRow detail={error} icon="!" label="Something went wrong" tone="error" />
        )}

        {connections.length > 0 && (
          <div className="projects-toolbar">
            <SearchInput
              onChange={setFilter}
              placeholder="Search projects"
              value={filter}
            />
            <span className="projects-toolbar__count">
              {connections.length}{" "}
              {connections.length === 1 ? "project" : "projects"}
            </span>
          </div>
        )}

        {connectionsLoading ? (
          <StateRow
            detail="Loading repositories connected to this workspace."
            label="Loading projects"
            tone="loading"
          />
        ) : connections.length === 0 && hasProviderConnections === false ? (
          <div className="projects-empty">
            <Eyebrow>No accounts connected</Eyebrow>
            <h3>Connect GitHub or GitLab to get started</h3>
            <p>
              No repository providers are connected yet. Open workspace settings to
              connect your GitHub or GitLab account — then you can add projects here.
            </p>
            <div className="dashboard-empty-actions">
              <Link className="button button--primary" to="/workspace">
                Go to settings
              </Link>
              <Button
                onClick={() => {
                  setConnectInitialKey(null);
                  setConnectDialogOpen(true);
                }}
                variant="secondary"
              >
                Connect now
              </Button>
            </div>
          </div>
        ) : connections.length === 0 ? (
          <div className="projects-empty">
            <Eyebrow>No projects yet</Eyebrow>
            <h3>Add your first repository</h3>
            <p>
              Your accounts are connected. Add an existing repository, or generate a
              brand new project and publish it.
            </p>
            <div className="dashboard-empty-actions">
              <Button
                onClick={() => {
                  setConnectInitialKey(null);
                  setConnectDialogOpen(true);
                }}
              >
                Add repository
              </Button>
              <Button onClick={() => setCreatingProject(true)} variant="secondary">
                Create project
              </Button>
            </div>
          </div>
        ) : filteredConnections.length === 0 ? (
          <div className="projects-empty projects-empty--compact">
            <h3>No matches</h3>
            <p>No connected project matches “{filter}”.</p>
          </div>
        ) : (
          <div className="projects-grid">
            {filteredConnections.map((connection) => (
              <ProjectCard
                analysisError={analysisErrorByRepository[connection.id] ?? null}
                analyzing={analyzingId === connection.id}
                connection={connection}
                key={connection.id}
                onAnalyze={() => void handleAnalyzeRepository(connection)}
                onDelete={() => void handleDisconnect(connection.id)}
                onOpen={() => setSelectedRepositoryId(connection.id)}
              />
            ))}
          </div>
        )}
      </section>
    );
  }

  return (
    <WorkspaceFrame active="projects">
      {body}
      <ConnectRepositoryDialog
        existingConnections={connections}
        initialProviderKey={connectInitialKey}
        onConnected={handleConnected}
        onOpenChange={(open) => {
          setConnectDialogOpen(open);
          if (!open) {
            listConnections()
              .then((items) => setHasProviderConnections(items.length > 0))
              .catch(() => undefined);
          }
        }}
        open={connectDialogOpen}
      />
    </WorkspaceFrame>
  );
}

type ProjectCardProps = Readonly<{
  connection: RepositoryConnection;
  analyzing: boolean;
  analysisError: string | null;
  onOpen: () => void;
  onAnalyze: () => void;
  onDelete: () => void;
}>;

function ProjectCard({
  connection,
  analyzing,
  analysisError,
  onOpen,
  onAnalyze,
  onDelete,
}: ProjectCardProps) {
  const summary = connection.analysisSummary;
  const canAnalyze = connection.provider === "github";
  return (
    <div className="project-card-wrap">
      <button className="project-card" onClick={onOpen} type="button">
        <div className="project-card__head">
          <span aria-hidden="true" className="project-card__avatar">
            {connection.owner.charAt(0).toUpperCase()}
          </span>
          <div className="project-card__id">
            <strong>{connection.name}</strong>
            <span>{connection.owner}</span>
          </div>
          {summary ? (
            <span
              className={`project-card__score ${statusBadgeClassName(summary.overallStatus)}`}
            >
              {formatScore(summary.overallScore)}
            </span>
          ) : (
            <span className="project-card__score project-card__score--pending">—</span>
          )}
        </div>
        <div className="project-card__meta">
          <span>{formatProvider(connection.provider)}</span>
          <span>
            {summary
              ? `Analyzed ${formatRelative(summary.analyzedAt)}`
              : `Connected ${formatRelative(connection.connectedAt)}`}
          </span>
        </div>
        <div className="project-card__footer">
          {analyzing ? (
            <span className="project-card__runs">Analyzing…</span>
          ) : analysisError ? (
            <span className="project-card__runs project-card__runs--error">
              Analysis failed
            </span>
          ) : summary ? (
            <>
              <Badge className={statusBadgeClassName(summary.overallStatus)}>
                {statusMeta(summary.overallStatus).label}
              </Badge>
              <span className="project-card__runs">
                {connection.analysisRunCount}{" "}
                {connection.analysisRunCount === 1 ? "run" : "runs"}
              </span>
            </>
          ) : (
            <span className="project-card__runs">Not analyzed yet</span>
          )}
        </div>
      </button>
      <div className="project-card__quick">
        {canAnalyze && (
          <button
            aria-label={`Analyze ${connection.owner}/${connection.name}`}
            className="icon-button project-card__action"
            disabled={analyzing}
            onClick={onAnalyze}
            title={summary ? "Re-analyze" : "Analyze"}
            type="button"
          >
            <IconAnalyze />
          </button>
        )}
        <button
          aria-label={`Remove ${connection.owner}/${connection.name}`}
          className="icon-button icon-button--danger project-card__action"
          onClick={onDelete}
          title="Remove project"
          type="button"
        >
          <IconTrash />
        </button>
      </div>
    </div>
  );
}

type ProjectDetailProps = Readonly<{
  analysis: RepositoryAnalysis | null;
  connection: RepositoryConnection | null;
  delta: RepositoryAnalysisDelta | null;
  error: string | null;
  loading: boolean;
  loadingStored: boolean;
  onAnalyze: (connection: RepositoryConnection) => Promise<void>;
  onConnect: () => void;
  onCreate: () => void;
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
  onCreate,
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
        <div className="dashboard-empty-actions">
          <button className="button button--primary" onClick={onCreate} type="button">
            Create project
          </button>
          <button className="button button--secondary" onClick={onConnect} type="button">
            Connect repository
          </button>
        </div>
      </Card>
    );
  }

  if (loading || loadingStored) {
    return (
      <Card as="section" className="project-detail">
        <ProjectDetailHeader
          analysis={analysis}
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
        analysis={analysis}
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
        <AnalysisBreakdown
          analysis={analysis}
          delta={delta}
          onReanalyze={() => onAnalyze(connection)}
          reanalyzing={loading}
        />
      ) : connection.provider !== "github" ? (
        <div className="analysis-empty">
          <div>
            <Eyebrow>Analysis</Eyebrow>
            <h3>GitLab analysis is coming soon</h3>
            <p>
              This GitLab project is connected to the workspace. Pipeline analysis for
              GitLab is on the way — GitHub projects can be analyzed today.
            </p>
          </div>
        </div>
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
  analysis: RepositoryAnalysis | null;
  connection: RepositoryConnection;
  hasAnalysis: boolean;
  loading: boolean;
  onAnalyze: (connection: RepositoryConnection) => Promise<void>;
  onDisconnect: (id: string) => void;
}>;

function ProjectDetailHeader({
  analysis,
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
        {analysis && (
          <div className="project-detail__analysis-meta">
            <Badge>{formatProvider(analysis.analysis.provider)}</Badge>
            <Badge
              className={statusBadgeClassName(analysis.analysis.overallStatus)}
            >
              {statusMeta(analysis.analysis.overallStatus).label}
            </Badge>
            <span>Run {analysis.runNumber}</span>
            <span>
              Analyzed {formatRelative(analysis.analyzedAt)}
            </span>
            <code>{analysis.workflowPath}</code>
          </div>
        )}
      </div>
      <div className="project-detail__side">
        <div className="project-detail__actions">
          {connection.provider === "github" ? (
            <Button
              className="button--small"
              disabled={loading}
              onClick={() => onAnalyze(connection)}
              variant="secondary"
            >
              {loading ? "Analyzing" : hasAnalysis ? "Re-analyze" : "Run analysis"}
            </Button>
          ) : (
            <Badge title="GitLab analysis is coming soon.">Analysis coming soon</Badge>
          )}
          <a
            aria-label={`Open ${connection.owner}/${connection.name}`}
            className="icon-button"
            href={connection.url}
            rel="noreferrer"
            target="_blank"
            title="Open repository"
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
        {analysis && (
          <div className="analysis-score project-detail__score">
            <strong>{formatScore(analysis.analysis.overallScore)}</strong>
            <span>{formatMaturityLevel(analysis.analysis.overallLevel)}</span>
          </div>
        )}
      </div>
    </header>
  );
}

type AnalysisBreakdownProps = Readonly<{
  analysis: RepositoryAnalysis;
  delta: RepositoryAnalysisDelta | null;
  onReanalyze: () => Promise<void>;
  reanalyzing: boolean;
}>;

type AnalysisDetailTab = "findings" | "quality" | "delta";

function AnalysisBreakdown({
  analysis,
  delta,
  onReanalyze,
  reanalyzing,
}: AnalysisBreakdownProps) {
  const [activeTab, setActiveTab] = useState<AnalysisDetailTab>("quality");
  const [findingFilter, setFindingFilter] = useState<FindingFilter>("missing");
  const [findingDimension, setFindingDimension] = useState<string | null>(null);
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
      <div className="analysis-tabs" aria-label="Analysis detail sections">
        <button
          className={activeTab === "findings" ? "analysis-tabs__item--active" : ""}
          onClick={() => setActiveTab("findings")}
          type="button"
        >
          Findings {openFindings.length}
        </button>
        <button
          className={activeTab === "quality" ? "analysis-tabs__item--active" : ""}
          onClick={() => setActiveTab("quality")}
          type="button"
        >
          Quality areas
        </button>
        <button
          className={activeTab === "delta" ? "analysis-tabs__item--active" : ""}
          onClick={() => setActiveTab("delta")}
          type="button"
        >
          Delta
        </button>
      </div>

      {activeTab === "findings" && (
        <>
          <DeltaInlineNotice delta={delta} onOpenDelta={() => setActiveTab("delta")} />
          <FindingsTable
            dimensionFilter={findingDimension}
            filter={findingFilter}
            findings={findings}
            onClearDimensionFilter={() => setFindingDimension(null)}
            onFilterChange={setFindingFilter}
            onReanalyze={onReanalyze}
            reanalyzing={reanalyzing}
            workflowContent={analysis.workflowContent}
            workflowPath={analysis.workflowPath}
          />
        </>
      )}

      {activeTab === "quality" && (
        <>
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
            onSelectMissingDimension={(dimension) => {
              setFindingFilter("missing");
              setFindingDimension(dimension);
              setActiveTab("findings");
            }}
          />
        </>
      )}

      {activeTab === "delta" && <AnalysisDeltaPanel delta={delta} />}
    </div>
  );
}

type DeltaInlineNoticeProps = Readonly<{
  delta: RepositoryAnalysisDelta | null;
  onOpenDelta: () => void;
}>;

function DeltaInlineNotice({ delta, onOpenDelta }: DeltaInlineNoticeProps) {
  if (!delta?.hasPrevious || !delta.overall) {
    return null;
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

  if (improved === 0 && worsened === 0 && delta.overall.direction === "unchanged") {
    return null;
  }

  return (
    <button className="delta-inline" onClick={onOpenDelta} type="button">
      <span>
        Latest delta: {formatDeltaDirection(delta.overall.direction)}
      </span>
      <strong>
        {improved} improved · {worsened} worsened
      </strong>
    </button>
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
  onSelectMissingDimension: (dimension: string) => void;
}>;

function QualityAreaTable({
  dimensions,
  findings,
  onSelectMissingDimension,
}: QualityAreaTableProps) {
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
          const missing = areaFindings.filter(
            (finding) => finding.finding.type === "MISSING",
          ).length;
          const passed = areaFindings.length - open;
          const status = statusMeta(dimension.status);
          return (
            <button
              className="quality-area-row"
              key={dimension.dimension}
              onClick={() => onSelectMissingDimension(dimension.dimension)}
              type="button"
            >
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
                    : formatMaturityLevel(dimension.level)}
                </span>
              </div>
              <div className="quality-area-row__counts">
                <span>{open} open</span>
                <span>{missing} missing</span>
                <span>{passed} detected</span>
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}

type FindingFilter = "missing" | "smell" | "passed" | "all";

type FindingsTableProps = Readonly<{
  dimensionFilter: string | null;
  filter: FindingFilter;
  findings: FlattenedAnalysisFinding[];
  onClearDimensionFilter: () => void;
  onFilterChange: (filter: FindingFilter) => void;
  onReanalyze: () => Promise<void>;
  reanalyzing: boolean;
  workflowContent: string | null;
  workflowPath: string;
}>;

function FindingsTable({
  dimensionFilter,
  filter,
  findings,
  onClearDimensionFilter,
  onFilterChange,
  onReanalyze,
  reanalyzing,
  workflowContent,
  workflowPath,
}: FindingsTableProps) {
  const [selectedFindingKey, setSelectedFindingKey] = useState<string | null>(null);
  const scopedFindings = dimensionFilter
    ? findings.filter(
        (finding) => finding.dimension.dimension === dimensionFilter,
      )
    : findings;
  const missingFindings = scopedFindings.filter(
    (finding) => finding.finding.type === "MISSING",
  );
  const smellFindings = scopedFindings.filter(
    (finding) => finding.finding.type === "SMELL",
  );
  const passedFindings = scopedFindings.filter(
    (finding) => finding.finding.type === "POSITIVE",
  );
  const passedCount = passedFindings.length;
  const filteredFindings = scopedFindings.filter((finding) => {
    if (filter === "missing") {
      return finding.finding.type === "MISSING";
    }
    if (filter === "smell") {
      return finding.finding.type === "SMELL";
    }
    if (filter === "passed") {
      return finding.finding.type === "POSITIVE";
    }
    return true;
  });
  const scopedDimension = dimensionFilter
    ? dimensionMeta(dimensionFilter)
    : null;

  const selectedFinding = useMemo(
    () =>
      selectedFindingKey
        ? findings.find((finding) => findingKey(finding) === selectedFindingKey) ?? null
        : null,
    [findings, selectedFindingKey],
  );

  return (
    <section className="findings-panel">
      <header className="scanner-section-header">
        <div>
          <Eyebrow>Findings</Eyebrow>
          <h4>Issue list</h4>
        </div>
        <div className="findings-filter" aria-label="Finding filter">
          <button
            className={
              filter === "missing" ? "findings-filter__item--active" : ""
            }
            onClick={() => onFilterChange("missing")}
            type="button"
          >
            Missing {missingFindings.length}
          </button>
          <button
            className={
              filter === "smell" ? "findings-filter__item--active" : ""
            }
            onClick={() => onFilterChange("smell")}
            type="button"
          >
            Needs review {smellFindings.length}
          </button>
          <button
            className={
              filter === "passed" ? "findings-filter__item--active" : ""
            }
            onClick={() => onFilterChange("passed")}
            type="button"
          >
            Detected {passedCount}
          </button>
          <button
            className={filter === "all" ? "findings-filter__item--active" : ""}
            onClick={() => onFilterChange("all")}
            type="button"
          >
            All {scopedFindings.length}
          </button>
        </div>
      </header>

      {scopedDimension && (
        <div className="findings-scope">
          <div>
            <span>{findingFilterTitle(filter).toLowerCase()} for</span>
            <strong>{scopedDimension.label}</strong>
          </div>
          <button onClick={onClearDimensionFilter} type="button">
            Show all categories
          </button>
        </div>
      )}

      {filteredFindings.length === 0 ? (
        <div className="findings-empty">
          <h4>No findings in this view</h4>
          <p>Switch filters to inspect detected checks or all analyzer output.</p>
        </div>
      ) : (
        <FindingsContent
          dimensionFilter={dimensionFilter}
          filter={filter}
          findings={filteredFindings}
          onSelectFinding={(finding) => setSelectedFindingKey(findingKey(finding))}
        />
      )}
      <FindingSourceDialog
        finding={selectedFinding}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedFindingKey(null);
          }
        }}
        onReanalyze={onReanalyze}
        reanalyzing={reanalyzing}
        workflowContent={workflowContent}
        workflowPath={workflowPath}
      />
    </section>
  );
}

type FindingsContentProps = Readonly<{
  dimensionFilter: string | null;
  filter: FindingFilter;
  findings: FlattenedAnalysisFinding[];
  onSelectFinding: (finding: FlattenedAnalysisFinding) => void;
}>;

function FindingsContent({
  dimensionFilter,
  filter,
  findings,
  onSelectFinding,
}: FindingsContentProps) {
  if (filter === "missing" && !dimensionFilter) {
    return (
      <div className="findings-groups">
        {groupFindingsByDimension(findings).map((group) => (
          <FindingGroup
            description={group.description}
            findings={group.findings}
            key={group.dimension}
            onSelectFinding={onSelectFinding}
            title={group.label}
          />
        ))}
      </div>
    );
  }

  return (
    <FindingGroup
      description={findingFilterDescription(filter)}
      findings={findings}
      onSelectFinding={onSelectFinding}
      title={findingFilterTitle(filter)}
    />
  );
}

function groupFindingsByDimension(findings: FlattenedAnalysisFinding[]) {
  const groups = new Map<
    string,
    {
      description: string;
      dimension: string;
      findings: FlattenedAnalysisFinding[];
      label: string;
    }
  >();

  findings.forEach((finding) => {
    const current = groups.get(finding.dimension.dimension);
    if (current) {
      current.findings.push(finding);
      return;
    }
    groups.set(finding.dimension.dimension, {
      description: finding.dimensionDescription,
      dimension: finding.dimension.dimension,
      findings: [finding],
      label: finding.dimensionLabel,
    });
  });

  return Array.from(groups.values());
}

type FindingGroupProps = Readonly<{
  description: string;
  findings: FlattenedAnalysisFinding[];
  onSelectFinding: (finding: FlattenedAnalysisFinding) => void;
  title: string;
}>;

function FindingGroup({
  description,
  findings,
  onSelectFinding,
  title,
}: FindingGroupProps) {
  if (findings.length === 0) {
    return null;
  }

  return (
    <section className="findings-group">
      <header className="findings-group__header">
        <div>
          <h5>{title}</h5>
          <p>{description}</p>
        </div>
        <span>
          {findings.length} {findings.length === 1 ? "finding" : "findings"}
        </span>
      </header>
      <div className="findings-list" aria-label={title}>
        {findings.map((finding, index) => (
          <FindingRow
            finding={finding}
            key={`${findingKey(finding)}-${index}`}
            onSelect={onSelectFinding}
          />
        ))}
      </div>
    </section>
  );
}

function findingFilterTitle(filter: FindingFilter): string {
  if (filter === "missing") {
    return "Missing practices";
  }
  if (filter === "smell") {
    return "Needs review";
  }
  if (filter === "passed") {
    return "Detected checks";
  }
  return "All findings";
}

function findingFilterDescription(filter: FindingFilter): string {
  if (filter === "missing") {
    return "Practices Scaffy expected but could not find in this workflow.";
  }
  if (filter === "smell") {
    return "Existing workflow configuration that deserves review before it becomes a reliability or security problem.";
  }
  if (filter === "passed") {
    return "Signals Scaffy found and counted toward the repository score.";
  }
  return "Every analyzer signal for this workflow, including missing practices, review items, and detected checks.";
}

function findingKey(finding: FlattenedAnalysisFinding): string {
  return [
    finding.finding.ruleId,
    finding.finding.dimension,
    finding.finding.capability,
    finding.finding.type,
    finding.finding.location ?? "",
    finding.finding.evidence ?? "",
  ].join("|");
}

type FindingRowProps = Readonly<{
  finding: FlattenedAnalysisFinding;
  onSelect: (finding: FlattenedAnalysisFinding) => void;
}>;

function FindingRow({ finding, onSelect }: FindingRowProps) {
  return (
    <button
      className={`finding-row finding-row--${finding.finding.type.toLowerCase()}`}
      onClick={() => onSelect(finding)}
      type="button"
    >
      <span className="finding-row__summary">
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
      </span>
    </button>
  );
}

type FindingSourceDialogProps = Readonly<{
  finding: FlattenedAnalysisFinding | null;
  onOpenChange: (open: boolean) => void;
  onReanalyze: () => Promise<void>;
  reanalyzing: boolean;
  workflowContent: string | null;
  workflowPath: string;
}>;

function FindingSourceDialog({
  finding,
  onOpenChange,
  onReanalyze,
  reanalyzing,
  workflowContent,
  workflowPath,
}: FindingSourceDialogProps) {
  const source = finding?.finding.source ?? null;
  const canShowSource = Boolean(finding && source && workflowContent);

  async function handleReanalyze() {
    await onReanalyze();
    onOpenChange(false);
  }

  return (
    <Dialog.Root onOpenChange={onOpenChange} open={Boolean(finding)}>
      <Dialog.Portal>
        <Dialog.Overlay className="source-dialog__overlay" />
        <Dialog.Content className="source-dialog">
          <header className="source-dialog__header">
            <div>
              <Eyebrow>Workflow source</Eyebrow>
              <Dialog.Title className="source-dialog__title">
                {finding?.ruleLabel ?? "Finding source"}
              </Dialog.Title>
              <Dialog.Description className="source-dialog__description">
                {workflowPath}
              </Dialog.Description>
            </div>
            <Dialog.Close className="icon-button" aria-label="Close source viewer">
              <IconClose />
            </Dialog.Close>
          </header>

          {finding && canShowSource && source && workflowContent ? (
            <>
              <div className="source-dialog__meta">
                <Badge
                  className={`finding-badge finding-badge--${finding.finding.type.toLowerCase()}`}
                >
                  {finding.typeLabel}
                </Badge>
                <span className="source-dialog__meta-group">
                  <span>{finding.dimensionLabel}</span>
                  <span>{finding.capabilityLabel}</span>
                </span>
                <span className="source-dialog__meta-location">
                  <code>{source.path}</code>
                  <span>
                    Line {source.startLine}
                    {source.endLine !== source.startLine
                      ? `-${source.endLine}`
                      : ""}
                  </span>
                </span>
              </div>
              {finding.finding.evidence && (
                <p className="source-dialog__evidence">
                  {finding.finding.evidence}
                </p>
              )}
              <SourceCodeViewer content={workflowContent} source={source} />
            </>
          ) : (
            <div className="source-dialog__empty">
              <h4>Exact source is not available</h4>
              <p>
                {workflowContent
                  ? "This finding does not map to a concrete YAML node."
                  : "This analysis was created before Scaffy stored workflow source. Re-run the analyzer to enable click-to-code navigation."}
              </p>
              {!workflowContent && (
                <Button disabled={reanalyzing} onClick={handleReanalyze}>
                  {reanalyzing ? "Analyzing" : "Re-analyze repository"}
                </Button>
              )}
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

type SourceCodeViewerProps = Readonly<{
  content: string;
  source: NonNullable<CapabilityFinding["source"]>;
}>;

function SourceCodeViewer({ content, source }: SourceCodeViewerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const monacoRef = useRef<typeof Monaco | null>(null);
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null);
  const decorationsRef = useRef<Monaco.editor.IEditorDecorationsCollection | null>(
    null,
  );
  const [editorReadyKey, setEditorReadyKey] = useState(0);

  useEffect(() => {
    if (!containerRef.current) {
      return undefined;
    }

    let disposed = false;
    let model: Monaco.editor.ITextModel | null = null;
    let editor: Monaco.editor.IStandaloneCodeEditor | null = null;

    void Promise.all([
      import("monaco-editor/esm/vs/editor/editor.api.js"),
      import("monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution.js"),
      import("monaco-editor/min/vs/editor/editor.main.css"),
    ]).then(([monacoModule]) => {
      if (disposed || !containerRef.current) {
        return;
      }

      const monacoApi = monacoModule as unknown as typeof Monaco;
      monacoRef.current = monacoApi;
      model = monacoApi.editor.createModel(content, "yaml");
      editor = monacoApi.editor.create(containerRef.current, {
        automaticLayout: true,
        contextmenu: false,
        folding: true,
        fontFamily:
          "var(--font-mono), ui-monospace, SFMono-Regular, Menlo, monospace",
        fontSize: 12,
        glyphMargin: false,
        language: "yaml",
        lineDecorationsWidth: 0,
        lineNumbersMinChars: 3,
        minimap: { enabled: false },
        model,
        overviewRulerLanes: 0,
        readOnly: true,
        renderLineHighlight: "none",
        scrollBeyondLastLine: false,
        stickyScroll: { enabled: false },
        theme: "vs",
        wordWrap: "off",
      });
      editorRef.current = editor;
      setEditorReadyKey((current) => current + 1);
    });

    return () => {
      disposed = true;
      decorationsRef.current?.clear();
      decorationsRef.current = null;
      editorRef.current = null;
      editor?.dispose();
      model?.dispose();
    };
  }, [content]);

  useEffect(() => {
    const monacoApi = monacoRef.current;
    const editor = editorRef.current;
    if (!monacoApi || !editor) {
      return;
    }

    decorationsRef.current = editor.createDecorationsCollection([
      {
        options: {
          className: "source-monaco-line-highlight",
          isWholeLine: true,
        },
        range: new monacoApi.Range(
          source.startLine,
          1,
          source.endLine,
          Number.MAX_SAFE_INTEGER,
        ),
      },
    ]);
    editor.revealLineInCenter(
      source.startLine,
      monacoApi.editor.ScrollType.Smooth,
    );
  }, [editorReadyKey, source.endLine, source.startLine]);

  return (
    <div
      className="source-viewer source-viewer--monaco"
      ref={containerRef}
      role="region"
      aria-label="Workflow YAML"
    />
  );
}


type CreateProjectPanelProps = Readonly<{
  onCancel: () => void;
  onCreated: (
    connection: RepositoryConnection,
    analysis: RepositoryAnalysis | null,
  ) => void;
}>;

type CreateProjectState = {
  backend: string;
  backendRuntime: string;
  backendVersion: string;
  frontend: string;
  frontendRuntime: string;
  frontendVersion: string;
  pipelineMaturity: string;
  projectName: string;
};

type CreateProjectStatus =
  | { kind: "idle" }
  | { kind: "generating"; job?: InitJob }
  | { kind: "publishing"; initJob: InitJob; publication?: RepositoryPublication }
  | { kind: "analyzing"; initJob: InitJob; publication: RepositoryPublication }
  | {
      kind: "success";
      initJob: InitJob;
      publication: RepositoryPublication;
      analysis: RepositoryAnalysis | null;
    }
  | { kind: "error"; message: string; job?: InitJob; publication?: RepositoryPublication };

const createProjectInitialState: CreateProjectState = {
  backend: "",
  backendRuntime: "",
  backendVersion: "",
  frontend: "",
  frontendRuntime: "",
  frontendVersion: "",
  pipelineMaturity: "",
  projectName: "",
};

function CreateProjectPanel({ onCancel, onCreated }: CreateProjectPanelProps) {
  const [catalog, setCatalog] = useState<InitCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [state, setState] = useState<CreateProjectState>(createProjectInitialState);
  const [status, setStatus] = useState<CreateProjectStatus>({ kind: "idle" });

  useEffect(() => {
    let mounted = true;
    getInitCatalog()
      .then((next) => {
        if (!mounted) return;
        setCatalog(next);
        setState((current) => withCreateCatalogDefaults(current, next));
      })
      .catch((err: unknown) => {
        if (!mounted) return;
        setCatalogError(
          err instanceof Error ? err.message : "Could not load initializer catalog.",
        );
      });
    return () => {
      mounted = false;
    };
  }, []);

  const projectNameError = useMemo(
    () => validateCreateProjectName(state.projectName),
    [state.projectName],
  );

  const inFlight =
    status.kind === "generating" || status.kind === "publishing" || status.kind === "analyzing";

  const canSubmit = Boolean(
    catalog &&
      !inFlight &&
      state.projectName &&
      !projectNameError &&
      state.frontend &&
      state.frontendVersion &&
      state.frontendRuntime &&
      state.backend &&
      state.backendVersion &&
      state.backendRuntime &&
      state.pipelineMaturity,
  );

  function update<K extends keyof CreateProjectState>(
    key: K,
    value: CreateProjectState[K],
  ) {
    setStatus((current) => (current.kind === "error" ? { kind: "idle" } : current));
    setState((current) => {
      const next = { ...current, [key]: value };
      if (!catalog) return next;
      if (key === "frontend") {
        return withCreateStackDefaults(next, catalog.frontends, "frontend");
      }
      if (key === "frontendVersion") {
        return withCreateRuntimeDefault(next, catalog.frontends, "frontend");
      }
      if (key === "backend") {
        return withCreateStackDefaults(next, catalog.backends, "backend");
      }
      if (key === "backendVersion") {
        return withCreateRuntimeDefault(next, catalog.backends, "backend");
      }
      return next;
    });
  }

  async function startCreateProject() {
    if (!catalog || !canSubmit) return;
    setStatus({ kind: "generating" });
    try {
      const maturity = catalog.maturityPresets.find(
        (preset) => preset.id === state.pipelineMaturity,
      );
      const created = await createInitJob({
        projectName: state.projectName,
        frontend: state.frontend,
        frontendVersion: state.frontendVersion,
        frontendRuntime: state.frontendRuntime,
        backend: state.backend,
        backendVersion: state.backendVersion,
        backendRuntime: state.backendRuntime,
        pipeline: "github-actions",
        pipelineMaturity: state.pipelineMaturity,
        includeDocker: Boolean(maturity?.dockerRequired),
      });

      let initJob = created;
      setStatus({ kind: "generating", job: initJob });
      while (initJob.status === "queued" || initJob.status === "running") {
        await delay(1400);
        initJob = await getInitJob(created.jobId);
        setStatus({ kind: "generating", job: initJob });
      }
      if (initJob.status !== "succeeded") {
        throw new Error(initJob.errorMessage || "Project generation failed.");
      }

      const publicationCreated = await createRepositoryPublication({
        initJobId: initJob.jobId,
        repositoryName: state.projectName,
        description: "Generated by Scaffy.",
      });
      let publication = publicationCreated;
      setStatus({ kind: "publishing", initJob, publication });
      while (publication.status === "queued" || publication.status === "running") {
        await delay(1400);
        publication = await getRepositoryPublication(publicationCreated.publicationJobId);
        setStatus({ kind: "publishing", initJob, publication });
      }
      if (publication.status !== "succeeded" || !publication.repositoryConnection) {
        throw new Error(publication.errorMessage || "GitHub publication failed.");
      }

      setStatus({ kind: "analyzing", initJob, publication });
      let analysis: RepositoryAnalysis | null = null;
      try {
        analysis = await analyzeRepository(publication.repositoryConnection.id);
      } catch {
        analysis = null;
      }
      setStatus({ kind: "success", initJob, publication, analysis });
      onCreated(publication.repositoryConnection, analysis);
    } catch (err) {
      setStatus({
        kind: "error",
        message: err instanceof Error ? err.message : "Project creation failed.",
      });
    }
  }

  return (
    <section
      aria-labelledby="create-project-title"
      className="init-band create-project-band"
    >
      <header className="create-project-header">
        <div>
          <Eyebrow>Create project</Eyebrow>
          <h2 id="create-project-title">
            Generate a project and publish it to GitHub.
          </h2>
          <p>
            Pick a stack and a maturity target. Scaffy generates the repository,
            publishes it under your GitHub account, connects it here, and runs the first
            analysis.
          </p>
        </div>
        <Button
          disabled={inFlight}
          onClick={onCancel}
          variant="secondary"
        >
          Cancel
        </Button>
      </header>

      {catalogError && (
        <StateRow detail={catalogError} icon="!" label="Catalog unavailable" tone="error" />
      )}

      {!catalog && !catalogError && <CreateProjectSkeleton />}

      {catalog && (
        <div className="create-project-stack">
          <div className="init-config">
            <WizardStep
              index={1}
              title="Project details"
              hint="The name of the new GitHub repository (created private)."
            >
              <div className="project-details">
                <div className="project-details__field">
                  <label htmlFor="create-project-name">Repository name</label>
                  <TextInput
                    aria-describedby="create-project-name-help"
                    aria-invalid={projectNameError !== null}
                    autoComplete="off"
                    id="create-project-name"
                    onChange={(event) => update("projectName", event.target.value)}
                    placeholder="my-scaffy-app"
                    value={state.projectName}
                  />
                  <p
                    className={`project-details__hint${
                      projectNameError ? " project-details__hint--error" : ""
                    }`}
                    id="create-project-name-help"
                  >
                    {projectNameError ??
                      "Lowercase letters, digits, hyphens · 2–64 characters · must start with a letter."}
                  </p>
                </div>
              </div>
            </WizardStep>

            <WizardStep
              index={2}
              title="Frontend"
              hint="Pick a UI framework, version, and runtime."
            >
              <StackPresetGroup
                group="frontend"
                onRuntimeSelect={(id) => update("frontendRuntime", id)}
                onSelect={(id) => update("frontend", id)}
                onVersionSelect={(id) => update("frontendVersion", id)}
                options={catalog.frontends}
                selectedId={state.frontend}
                selectedRuntimeId={state.frontendRuntime}
                selectedVersionId={state.frontendVersion}
              />
            </WizardStep>

            <WizardStep
              index={3}
              title="Backend"
              hint="Choose the API framework that fits your team."
            >
              <StackPresetGroup
                group="backend"
                onRuntimeSelect={(id) => update("backendRuntime", id)}
                onSelect={(id) => update("backend", id)}
                onVersionSelect={(id) => update("backendVersion", id)}
                options={catalog.backends}
                selectedId={state.backend}
                selectedRuntimeId={state.backendRuntime}
                selectedVersionId={state.backendVersion}
              />
            </WizardStep>

            <WizardStep
              index={4}
              title="Maturity target"
              hint="Scaffy generates a GitHub Actions pipeline at this discipline level."
            >
              <MaturityPicker
                onSelect={(id) => update("pipelineMaturity", id)}
                presets={catalog.maturityPresets}
                selectedId={state.pipelineMaturity}
              />
            </WizardStep>
          </div>

          <CreateProjectReview
            canSubmit={canSubmit}
            catalog={catalog}
            inFlight={inFlight}
            onCancel={onCancel}
            onStart={startCreateProject}
            state={state}
            status={status}
          />
        </div>
      )}
    </section>
  );
}

type CreateProjectReviewProps = {
  canSubmit: boolean;
  catalog: InitCatalog;
  inFlight: boolean;
  onCancel: () => void;
  onStart: () => void;
  state: CreateProjectState;
  status: CreateProjectStatus;
};

function CreateProjectReview({
  canSubmit,
  catalog,
  inFlight,
  onCancel,
  onStart,
  state,
  status,
}: CreateProjectReviewProps) {
  const frontend = catalog.frontends.find((item) => item.id === state.frontend);
  const frontendVersion = frontend?.versions.find((v) => v.id === state.frontendVersion);
  const frontendRuntime = frontendVersion?.runtimes.find((r) => r.id === state.frontendRuntime);
  const backend = catalog.backends.find((item) => item.id === state.backend);
  const backendVersion = backend?.versions.find((v) => v.id === state.backendVersion);
  const backendRuntime = backendVersion?.runtimes.find((r) => r.id === state.backendRuntime);
  const maturity = catalog.maturityPresets.find((p) => p.id === state.pipelineMaturity);
  const dockerIncluded = Boolean(maturity?.dockerRequired);

  return (
    <div className="review">
      <div className="review__head">
        <span className="review__eyebrow">New project</span>
        <div className="review__name">{state.projectName || "unnamed-project"}</div>
      </div>

      <ul className="review__rows">
        <CreateReviewRow label="Frontend" iconId={frontend?.id}>
          {frontend ? (
            <>
              <strong>{frontend.name}</strong>
              <span>
                {[frontendVersion?.label, frontendRuntime?.label]
                  .filter(Boolean)
                  .join(" · ") || "—"}
              </span>
            </>
          ) : (
            <span className="review__placeholder">Not selected</span>
          )}
        </CreateReviewRow>

        <CreateReviewRow label="Backend" iconId={backend?.id}>
          {backend ? (
            <>
              <strong>{backend.name}</strong>
              <span>
                {[backendVersion?.label, backendRuntime?.label]
                  .filter(Boolean)
                  .join(" · ") || "—"}
              </span>
            </>
          ) : (
            <span className="review__placeholder">Not selected</span>
          )}
        </CreateReviewRow>

        <CreateReviewRow label="Pipeline" iconId="github-actions">
          <strong>GitHub Actions</strong>
          <span>{maturity ? maturity.label : "Pick a maturity level"}</span>
        </CreateReviewRow>

        <li className="review__row review__row--inline">
          <span className="review__label">Docker</span>
          <span className={`review__pill${dockerIncluded ? " review__pill--on" : ""}`}>
            {dockerIncluded ? "Included" : "Off"}
          </span>
        </li>
      </ul>

      <div className="review__actions">
        {status.kind === "success" ? (
          <Button onClick={onCancel} variant="secondary">
            Back to projects
          </Button>
        ) : status.kind === "error" ? (
          <>
            <Button disabled={!canSubmit} onClick={onStart} variant="download">
              Retry
            </Button>
            <Button onClick={onCancel} variant="secondary">
              Cancel
            </Button>
          </>
        ) : (
          <Button disabled={!canSubmit} onClick={onStart} variant="download">
            {inFlight
              ? status.kind === "generating"
                ? "Generating…"
                : status.kind === "publishing"
                  ? "Publishing…"
                  : "Analyzing…"
              : "Generate and publish"}
          </Button>
        )}
      </div>

      <CreateProjectStatusPanel status={status} />
    </div>
  );
}

type CreateReviewRowProps = {
  label: string;
  iconId?: string;
  children: React.ReactNode;
};

function CreateReviewRow({ label, iconId, children }: CreateReviewRowProps) {
  return (
    <li className="review__row">
      <span className="review__label">{label}</span>
      <span className="review__value">
        {iconId ? (
          <span className="review__icon" aria-hidden="true">
            <StackIcon id={iconId} />
          </span>
        ) : (
          <span className="review__icon review__icon--empty" aria-hidden="true" />
        )}
        <span className="review__value-text">{children}</span>
      </span>
    </li>
  );
}

function CreateProjectStatusPanel({ status }: { status: CreateProjectStatus }) {
  if (status.kind === "idle") {
    return (
      <div className="gen gen--idle">
        <p className="gen__hint">
          When you start, Scaffy will queue the generator, push to GitHub, and run the
          first analysis. You can leave this open and watch the live log.
        </p>
      </div>
    );
  }

  const title = createProjectStatusTitle(status);
  const copy = createProjectStatusCopy(status);
  const percent = createProjectPercent(status);
  const logs =
    status.kind === "generating"
      ? status.job?.logs
      : status.kind === "publishing" || status.kind === "analyzing" || status.kind === "success"
        ? status.publication?.logs
        : status.kind === "error"
          ? status.publication?.logs || status.job?.logs
          : undefined;

  const dotKind =
    status.kind === "success"
      ? "success"
      : status.kind === "error"
        ? "error"
        : "loading";

  return (
    <div className={`gen gen--${status.kind === "error" ? "error" : status.kind === "success" ? "success" : "loading"}`}>
      <div className="gen__head">
        <span className={`gen__dot gen__dot--${dotKind}`} aria-hidden="true" />
        <div>
          <strong>{title}</strong>
          <p>{copy}</p>
        </div>
      </div>

      <div className="gen__progress" aria-label="Project creation progress">
        <span style={{ width: `${percent}%` }} />
      </div>

      {logs && logs.length > 0 && (
        <div className="gen__log" aria-label="Project creation log">
          <div className="gen__log-bar">
            <span>Live log</span>
            <span>{logs.length} lines</span>
          </div>
          <pre>
            {logs.slice(-40).map((line) => (
              <span
                className={`gen__log-line gen__log-line--${line.stream}`}
                key={`${line.stream}-${line.id}`}
              >
                <span className="gen__log-stream">{line.stream}</span>
                {line.message}
                {"\n"}
              </span>
            ))}
          </pre>
        </div>
      )}

      {status.kind === "success" && status.publication.repositoryConnection && (
        <dl className="gen__meta">
          <div>
            <dt>Repo</dt>
            <dd>
              {status.publication.repositoryConnection.owner}/
              {status.publication.repositoryConnection.name}
            </dd>
          </div>
          <div>
            <dt>Analysis</dt>
            <dd>{status.analysis ? "Ready" : "Pending"}</dd>
          </div>
        </dl>
      )}
    </div>
  );
}

function CreateProjectSkeleton() {
  return (
    <div className="create-project-stack create-project-skeleton" aria-hidden="true">
      <div className="init-config">
        {[1, 2, 3, 4].map((i) => (
          <div className="init-step create-project-skeleton__step" key={i}>
            <div className="create-project-skeleton__head">
              <span className="create-project-skeleton__index" />
              <span className="create-project-skeleton__title" />
            </div>
            <div className="create-project-skeleton__row" />
            <div className="create-project-skeleton__row create-project-skeleton__row--short" />
          </div>
        ))}
      </div>
      <div className="review create-project-skeleton__summary">
        <div className="create-project-skeleton__row" />
        <div className="create-project-skeleton__row" />
        <div className="create-project-skeleton__row create-project-skeleton__row--short" />
      </div>
    </div>
  );
}

function createProjectStatusTitle(status: CreateProjectStatus): string {
  if (status.kind === "generating") {
    if (status.job?.status === "queued") return "Queued for generation";
    return "Generating project";
  }
  if (status.kind === "publishing") return "Publishing to GitHub";
  if (status.kind === "analyzing") return "Running first analysis";
  if (status.kind === "success") return "Project ready";
  if (status.kind === "error") return "Needs attention";
  return "Ready";
}

function createProjectStatusCopy(status: CreateProjectStatus): string {
  if (status.kind === "generating") {
    return status.job?.progress || "Waiting for the generator worker…";
  }
  if (status.kind === "publishing") {
    return status.publication?.progress || "Pushing the generated repo to GitHub…";
  }
  if (status.kind === "analyzing") {
    return "The repository is connected. Running Scaffy on the new workflow.";
  }
  if (status.kind === "success") {
    return status.analysis
      ? "Repository connected and the first analysis is ready."
      : "Repository connected. Analysis can be retried from the project view.";
  }
  if (status.kind === "error") return status.message;
  return "Configure the project and start the GitHub creation flow.";
}

function createProjectPercent(status: CreateProjectStatus): number {
  if (status.kind === "idle") return 0;
  if (status.kind === "generating") {
    if (status.job?.status === "queued") return 12;
    if (status.job?.status === "running") return 38;
    return 6;
  }
  if (status.kind === "publishing") return 64;
  if (status.kind === "analyzing") return 86;
  if (status.kind === "success") return 100;
  if (status.kind === "error") return 100;
  return 0;
}

function validateCreateProjectName(name: string): string | null {
  if (!name.trim()) return null;
  if (name.length < 2) return "Must be at least 2 characters.";
  if (name.length > 64) return "Must be 64 characters or fewer.";
  if (!/^[a-z][a-z0-9-]*[a-z0-9]$/.test(name)) {
    return "Lowercase letters, digits, hyphens only. Must start with a letter and end with a letter or digit.";
  }
  return null;
}

function withCreateCatalogDefaults(
  state: CreateProjectState,
  catalog: InitCatalog,
): CreateProjectState {
  const maturity =
    catalog.maturityPresets.find((preset) => preset.id === "l2") ??
    catalog.maturityPresets[0];
  const next = {
    ...state,
    frontend: state.frontend || catalog.frontends[0]?.id || "",
    backend: state.backend || catalog.backends[0]?.id || "",
    pipelineMaturity: state.pipelineMaturity || maturity?.id || "",
  };
  return withCreateStackDefaults(
    withCreateStackDefaults(next, catalog.frontends, "frontend"),
    catalog.backends,
    "backend",
  );
}

function withCreateStackDefaults(
  state: CreateProjectState,
  options: StackCatalogOption[],
  kind: "frontend" | "backend",
): CreateProjectState {
  const stack = findById(options, state[kind]);
  const versionKey = `${kind}Version` as const;
  const versionId = stack?.versions.some((version) => version.id === state[versionKey])
    ? state[versionKey]
    : stack?.defaultVersionId || stack?.versions[0]?.id || "";
  return withCreateRuntimeDefault({ ...state, [versionKey]: versionId }, options, kind);
}

function withCreateRuntimeDefault(
  state: CreateProjectState,
  options: StackCatalogOption[],
  kind: "frontend" | "backend",
): CreateProjectState {
  const stack = findById(options, state[kind]);
  const version = findById(stack?.versions ?? [], state[`${kind}Version`]);
  const runtimeKey = `${kind}Runtime` as const;
  const runtimeId = version?.runtimes.some((runtime) => runtime.id === state[runtimeKey])
    ? state[runtimeKey]
    : version?.defaultRuntimeId || version?.runtimes[0]?.id || "";
  return { ...state, [runtimeKey]: runtimeId };
}

function findById<T extends { id: string }>(items: T[], id: string): T | undefined {
  return items.find((item) => item.id === id);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
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

function IconAnalyze() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <path
        d="M2 9.5h2.5l1.5 3 3-8 1.5 5H14"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function IconBack() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      viewBox="0 0 16 16"
    >
      <path d="M9.5 3.5 5 8l4.5 4.5" strokeLinecap="round" strokeLinejoin="round" />
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
