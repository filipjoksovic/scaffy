import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
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
  listGitHubRepositories,
  listRepositoryConnections,
  type GitHubRepository,
  type RepositoryAnalysis,
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
  collectFindings,
  countIssues,
  dimensionMeta,
  findingKey,
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
  const [githubAccess, setGithubAccess] =
    useState<GitHubAccessState>("unknown");
  const [selectedRepositoryId, setSelectedRepositoryId] = useState<
    string | null
  >(null);
  const [analysisByRepository, setAnalysisByRepository] = useState<
    Record<string, RepositoryAnalysis>
  >({});
  const [analyzingId, setAnalyzingId] = useState<string | null>(null);
  const [analysisErrorByRepository, setAnalysisErrorByRepository] = useState<
    Record<string, string>
  >({});

  useEffect(() => {
    if (!user) {
      setConnections([]);
      setGitHubRepositories([]);
      setSelectedRepositoryId(null);
      setAnalysisByRepository({});
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
  const selectedAnalysisError = selectedConnection
    ? (analysisErrorByRepository[selectedConnection.id] ?? null)
    : null;
  const analyzedCount = connections.filter(
    (connection) => analysisByRepository[connection.id],
  ).length;

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
              onClick={() => void handleFetchGitHubRepositories()}
              variant="secondary"
            >
              {githubLoading ? "Syncing" : "Sync GitHub"}
            </Button>
            <a className="button button--primary" href="#quick-connect">
              Connect repository
            </a>
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
              error={selectedAnalysisError}
              loading={
                selectedConnection
                  ? analyzingId === selectedConnection.id
                  : false
              }
              onAnalyze={handleAnalyzeRepository}
              onDisconnect={handleDisconnect}
            />

            <RepositoryConnectPanel
              connections={connections}
              error={error}
              githubFilter={githubFilter}
              githubLoading={githubLoading}
              githubRepositories={githubRepositories}
              needsGitHubReconnect={needsGitHubReconnect}
              onConnectGithubRepository={handleConnectGitHubRepository}
              onFetchGithubRepositories={handleFetchGitHubRepositories}
              onGithubFilterChange={setGithubFilter}
              onRepositoryChange={setRepository}
              onSubmit={handleSubmit}
              repository={repository}
              submitting={submitting}
            />
          </div>
        </div>
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
            const analyzed = Boolean(analysisByRepository[connection.id]);
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
                    <span>{formatRelative(connection.connectedAt)}</span>
                  </span>
                  <span className="project-list__status">
                    {analyzed ? "Analyzed" : "Ready"}
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
  error: string | null;
  loading: boolean;
  onAnalyze: (connection: RepositoryConnection) => void;
  onDisconnect: (id: string) => void;
}>;

function ProjectDetail({
  analysis,
  connection,
  error,
  loading,
  onAnalyze,
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
        <a className="button button--primary" href="#quick-connect">
          Connect repository
        </a>
      </Card>
    );
  }

  if (loading) {
    return (
      <Card as="section" className="project-detail">
        <ProjectDetailHeader
          connection={connection}
          loading={loading}
          onAnalyze={onAnalyze}
          onDisconnect={onDisconnect}
        />
        <StateRow
          detail="Finding .github/workflows files and running the Scaffy capability analyzer."
          label="Analyzing repository"
          tone="loading"
        />
      </Card>
    );
  }

  return (
    <Card as="section" className="project-detail">
      <ProjectDetailHeader
        connection={connection}
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
        <AnalysisBreakdown analysis={analysis} />
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
  loading: boolean;
  onAnalyze: (connection: RepositoryConnection) => void;
  onDisconnect: (id: string) => void;
}>;

function ProjectDetailHeader({
  connection,
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
          {loading ? "Analyzing" : "Run analysis"}
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
          title="Disconnect"
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
}>;

function AnalysisBreakdown({ analysis }: AnalysisBreakdownProps) {
  const issueCount = analysis.analysis.dimensions.reduce(
    (total, dimension) => total + countIssues(dimension),
    0,
  );

  return (
    <div className="analysis-breakdown">
      <div className="analysis-panel__header">
        <div>
          <Eyebrow>Analysis result</Eyebrow>
          <h3>{analysis.repository}</h3>
          <p>
            {formatProvider(analysis.analysis.provider)} ·{" "}
            <code>{analysis.workflowPath}</code>
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
          {issueCount} open {issueCount === 1 ? "issue" : "issues"}
        </span>
      </div>

      <div className="analysis-dimensions">
        {analysis.analysis.dimensions.map((dimension) => (
          <DimensionBreakdown dimension={dimension} key={dimension.dimension} />
        ))}
      </div>
    </div>
  );
}

type DimensionBreakdownProps = Readonly<{
  dimension: DimensionAnalysis;
}>;

function DimensionBreakdown({ dimension }: DimensionBreakdownProps) {
  const positives = collectFindings(dimension, "POSITIVE");
  const smells = collectFindings(dimension, "SMELL");
  const missing = collectFindings(dimension, "MISSING");
  const status = statusMeta(dimension.status);
  const dimensionInfo = dimensionMeta(dimension.dimension);

  return (
    <section className="analysis-dimension">
      <header className="analysis-dimension__summary">
        <div>
          <strong>{dimensionInfo.label}</strong>
          <span>
            {dimension.status === "not_evaluated"
              ? status.label
              : `Level ${dimension.level}`}
          </span>
          <p>{dimensionInfo.description}</p>
        </div>
        <div className="analysis-dimension__score">
          {dimension.status === "not_evaluated"
            ? "—"
            : formatScore(dimension.score)}
        </div>
        <div className="analysis-dimension__findings">
          <span>{positives.length} positive</span>
          <span>{smells.length} smells</span>
          <span>{missing.length} missing</span>
        </div>
      </header>

      <div className="capability-list">
        {dimension.capabilityScores.map((capability) => (
          <CapabilityBreakdown
            capability={capability}
            key={capability.capability}
          />
        ))}
      </div>
    </section>
  );
}

type CapabilityBreakdownProps = Readonly<{
  capability: CapabilityScore;
}>;

function CapabilityBreakdown({ capability }: CapabilityBreakdownProps) {
  const meta = capabilityMeta(capability.capability);

  return (
    <article className="capability-row">
      <header className="capability-row__header">
        <div>
          <strong>{meta.label}</strong>
          <span>{meta.description}</span>
        </div>
        <div className="capability-row__score">
          <strong>{capability.points} pts</strong>
          <span>
            {capability.findings.length}{" "}
            {capability.findings.length === 1 ? "finding" : "findings"}
          </span>
        </div>
      </header>

      {capability.findings.length > 0 ? (
        <ul className="finding-list">
          {capability.findings.map((finding) => (
            <FindingItem finding={finding} key={findingKey(finding)} />
          ))}
        </ul>
      ) : (
        <p className="capability-row__empty">
          No evidence was emitted for this capability.
        </p>
      )}
    </article>
  );
}

type FindingItemProps = Readonly<{
  finding: CapabilityFinding;
}>;

function FindingItem({ finding }: FindingItemProps) {
  const type = findingTypeMeta(finding.type);
  const rule = ruleMeta(finding.ruleId);

  return (
    <li className="finding-item">
      <div className="finding-item__meta">
        <Badge
          className={`finding-badge finding-badge--${finding.type.toLowerCase()}`}
          title={type.description}
        >
          {type.label}
        </Badge>
        <strong className="finding-item__rule">{rule.label}</strong>
      </div>
      <span className="finding-item__description">{rule.description}</span>
      {finding.evidence && <p>{finding.evidence}</p>}
      {finding.location && <code>{finding.location}</code>}
    </li>
  );
}

type RepositoryConnectPanelProps = Readonly<{
  connections: RepositoryConnection[];
  error: string | null;
  githubFilter: string;
  githubLoading: boolean;
  githubRepositories: GitHubRepository[];
  needsGitHubReconnect: boolean;
  onConnectGithubRepository: (repo: GitHubRepository) => void;
  onFetchGithubRepositories: () => void;
  onGithubFilterChange: (value: string) => void;
  onRepositoryChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  repository: string;
  submitting: boolean;
}>;

function RepositoryConnectPanel({
  connections,
  error,
  githubFilter,
  githubLoading,
  githubRepositories,
  needsGitHubReconnect,
  onConnectGithubRepository,
  onFetchGithubRepositories,
  onGithubFilterChange,
  onRepositoryChange,
  onSubmit,
  repository,
  submitting,
}: RepositoryConnectPanelProps) {
  const filteredGithubRepositories = useMemo(() => {
    if (!githubFilter.trim()) return githubRepositories;
    const query = githubFilter.trim().toLowerCase();
    return githubRepositories.filter((repo) =>
      repo.fullName.toLowerCase().includes(query),
    );
  }, [githubFilter, githubRepositories]);

  return (
    <Card as="section" className="panel project-connect" id="quick-connect">
      <div className="panel__header">
        <div className="panel__heading">
          <Eyebrow>Add projects</Eyebrow>
          <h3>Connect another repository</h3>
          <p>
            Sync GitHub to pick from your account, or paste a repository
            manually.
          </p>
        </div>
        <div className="panel__actions">
          <Button
            className="button--small"
            disabled={githubLoading}
            onClick={onFetchGithubRepositories}
            variant="secondary"
          >
            {githubLoading
              ? "Syncing"
              : githubRepositories.length === 0
                ? "Sync GitHub"
                : "Refresh"}
          </Button>
        </div>
      </div>

      <div className="project-connect__body">
        <form className="quick-connect" onSubmit={onSubmit}>
          <label htmlFor="repository">Repository</label>
          <div className="input-row">
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
          <span className="quick-connect__hint">
            owner/repo · https://github.com/owner/repo
          </span>
          {error && (
            <div>
              <p className="form-error">{error}</p>
              {needsGitHubReconnect && (
                <div className="form-error__actions">
                  <a
                    className="button button--secondary button--small"
                    href={oauthLoginUrl.github}
                  >
                    Reconnect GitHub
                  </a>
                </div>
              )}
            </div>
          )}
        </form>

        <div className="github-picker">
          <div className="github-picker__header">
            <strong>Your GitHub repositories</strong>
            {githubRepositories.length > 0 && (
              <SearchInput
                onChange={onGithubFilterChange}
                placeholder="Filter repositories"
                value={githubFilter}
              />
            )}
          </div>

          {githubLoading ? (
            <StateRow
              detail="Calling /api/repositories/github."
              label="Syncing GitHub repositories"
              tone="loading"
            />
          ) : githubRepositories.length === 0 ? (
            <div className="empty-state empty-state--compact">
              <p>
                Press <strong>Sync GitHub</strong> to fetch repositories from
                your connected account.
              </p>
            </div>
          ) : filteredGithubRepositories.length === 0 ? (
            <div className="empty-state empty-state--compact">
              <p>No repositories match “{githubFilter}”.</p>
            </div>
          ) : (
            <ul className="gh-list">
              {filteredGithubRepositories.map((repo) => {
                const connected = connections.some(
                  (connection) =>
                    `${connection.owner}/${connection.name}`.toLowerCase() ===
                    repo.fullName.toLowerCase(),
                );
                return (
                  <li className="gh-list__item" key={repo.fullName}>
                    <div className="gh-list__info">
                      <span className="gh-list__name">{repo.fullName}</span>
                      <span className="gh-list__meta">
                        <span aria-hidden="true" className="dot" />
                        {repo.privateRepository ? "Private" : "Public"}
                      </span>
                    </div>
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
      </div>
    </Card>
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
