import { useMemo, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  ExternalLink,
  MinusCircle,
  TrendingDown,
  TrendingUp,
  XCircle,
} from "lucide-react";
import type {
  BranchHealth,
  RecentRunSummary,
  WorkflowMetricsResult,
} from "../api/repositories";
import { connectProviderUrl } from "../api/auth";
import { useWorkspace } from "../lib/workspace";
import { formatDuration, successRateColor } from "../lib/metrics";
import { formatRelativeTime } from "../lib/time";
import { Card, Eyebrow } from "./index";

type Props = {
  owner: string;
  repo: string;
  result?: WorkflowMetricsResult | null;
};

const STATUS_FALLBACKS: Record<string, string> = {
  TOKEN_MISSING: "Connect GitHub to view runtime metrics.",
  TOKEN_EXPIRED: "GitHub token expired. Reconnect to refresh metrics.",
  SCOPE_INSUFFICIENT: "Permission missing - reconnect with required scopes.",
  RATE_LIMITED: "GitHub rate limit hit. Try again later.",
  WORKFLOW_NOT_FOUND: "Workflow file not found in GitHub.",
  NO_RUNS_IN_WINDOW: "No runs in the last 30 days.",
  PROVIDER_ERROR: "Runtime metrics temporarily unavailable.",
};

const DEFAULT_FILTER = "all";

export function OperationalHealthCard({ owner, repo, result }: Props) {
  const { activeWorkspace } = useWorkspace();
  const [statusFilter, setStatusFilter] = useState(DEFAULT_FILTER);
  const [branchFilter, setBranchFilter] = useState(DEFAULT_FILTER);
  const [triggerFilter, setTriggerFilter] = useState(DEFAULT_FILTER);
  const [workflowFilter, setWorkflowFilter] = useState(DEFAULT_FILTER);
  const [showAllRuns, setShowAllRuns] = useState(false);

  if (!result || result.status === "UNSUPPORTED") return null;

  if (result.status !== "AVAILABLE") {
    return (
      <OperationalHealthStatus
        message={result.message ?? STATUS_FALLBACKS[result.status] ?? ""}
        status={result.status}
        workspaceId={activeWorkspace?.id ?? null}
      />
    );
  }

  const metrics = result.metrics;
  if (!metrics) {
    return null;
  }

  const successRate = Math.round(metrics.successRate * 100);
  const trend = trendLabel(metrics.durationTrend);
  const triggerEntries = Object.entries(metrics.triggerDistribution);
  const branchEntries = Object.entries(metrics.branchBreakdown);
  const lastRun = metrics.lastRunAt ? formatRelativeTime(metrics.lastRunAt) : null;
  const lastSuccess = metrics.lastSuccessAt
    ? formatRelativeTime(metrics.lastSuccessAt)
    : null;
  const showLastSuccess =
    lastSuccess && metrics.lastSuccessAt !== metrics.lastRunAt;

  const branchOptions = useMemo(
    () =>
      Array.from(
        new Set(metrics.recentRuns.map((run) => run.branch).filter((branch): branch is string => !!branch)),
      ).sort(),
    [metrics.recentRuns],
  );

  const triggerOptions = useMemo(
    () =>
      Array.from(
        new Set(metrics.recentRuns.map((run) => run.event).filter((event): event is string => !!event)),
      ).sort(),
    [metrics.recentRuns],
  );

  const workflowOptions = useMemo(
    () =>
      Array.from(
        new Set(
          metrics.recentRuns
            .map((run) => run.workflowName)
            .filter((workflow): workflow is string => !!workflow),
        ),
      ).sort(),
    [metrics.recentRuns],
  );

  const filteredRuns = useMemo(() => {
    return metrics.recentRuns.filter((run) => {
      const statusOk =
        statusFilter === DEFAULT_FILTER || run.conclusion === statusFilter;
      const branchOk =
        branchFilter === DEFAULT_FILTER || run.branch === branchFilter;
      const triggerOk =
        triggerFilter === DEFAULT_FILTER || run.event === triggerFilter;
      const workflowOk =
        workflowFilter === DEFAULT_FILTER || run.workflowName === workflowFilter;
      return statusOk && branchOk && triggerOk && workflowOk;
    });
  }, [
    metrics.recentRuns,
    statusFilter,
    branchFilter,
    triggerFilter,
    workflowFilter,
  ]);

  const visibleRuns = showAllRuns ? filteredRuns : filteredRuns.slice(0, 5);
  const hasMoreRuns = filteredRuns.length > 5;

  return (
    <Card as="section" className="operational-health operational-health--compact">
      <header className="operational-health__header operational-health__header--compact">
        <div className="operational-health__title-row">
          <div className="operational-health__title">
            <Eyebrow>Operational health</Eyebrow>
            <h4>Last {metrics.windowDays} days</h4>
          </div>
          {metrics.riskSummary ? (
            <span
              className={`operational-health__risk-badge operational-health__risk-badge--${metrics.riskSummary.level}`}
            >
              {metrics.riskSummary.label}
            </span>
          ) : null}
        </div>
        <span>
          Based on {metrics.totalRuns} workflow runs ({metrics.successCount} success / {metrics.failureCount} failed)
        </span>
      </header>

      <section className="operational-health__kpis">
        <div className="operational-health__kpi-card">
          <span>Success rate</span>
          <strong style={{ color: successRateColor(metrics.successRate) }}>
            {successRate}%
          </strong>
        </div>
        <div className="operational-health__kpi-card">
          <span>Failures (7d)</span>
          <strong className="operational-health__kpi-danger">{metrics.recentFailures7d}</strong>
        </div>
        <div className="operational-health__kpi-card">
          <span>Median duration</span>
          <strong>{formatDuration(metrics.medianDurationSec)}</strong>
        </div>
        <div className="operational-health__kpi-card">
          <span>p95 duration</span>
          <strong>{formatDuration(metrics.p95DurationSec)}</strong>
        </div>
      </section>

      <section className="operational-health__insights">
        {metrics.nextBestAction ? (
          <article className="operational-health__next-action">
            <Eyebrow>Next best action</Eyebrow>
            <strong>{metrics.nextBestAction.title}</strong>
            <p>{metrics.nextBestAction.detail}</p>
          </article>
        ) : null}

        {metrics.periodDelta ? (
          <article className="operational-health__delta">
            <Eyebrow>30d vs previous 30d</Eyebrow>
            <div className="operational-health__delta-grid">
              <div>
                <span>Success</span>
                <strong>{formatSignedPercent(metrics.periodDelta.successRateDelta)}</strong>
              </div>
              <div>
                <span>Failures</span>
                <strong>{formatSignedCount(metrics.periodDelta.failureCountDelta)}</strong>
              </div>
              <div>
                <span>Median</span>
                <strong>{formatSignedDuration(metrics.periodDelta.medianDurationDeltaSec)}</strong>
              </div>
              <div>
                <span>p95</span>
                <strong>{formatSignedDuration(metrics.periodDelta.p95DurationDeltaSec)}</strong>
              </div>
            </div>
          </article>
        ) : null}
      </section>

      {metrics.topFailureReasons && metrics.topFailureReasons.length > 0 ? (
        <section className="operational-health__section operational-health__section--tight">
          <Eyebrow>Top failure reasons</Eyebrow>
          <div className="operational-health__reasons">
            {metrics.topFailureReasons.map((reason) => (
              <span key={reason.reason} className="operational-health__reason-chip">
                {reason.reason} {reason.count}
              </span>
            ))}
          </div>
        </section>
      ) : null}

      {(metrics.regressionSignals && metrics.regressionSignals.length > 0) ||
      (metrics.flakyWorkflows && metrics.flakyWorkflows.length > 0) ? (
        <section className="operational-health__section operational-health__section--tight">
          <Eyebrow>Signals</Eyebrow>
          <div className="operational-health__signals">
            {(metrics.regressionSignals || []).map((signal) => (
              <span key={signal} className="operational-health__signal-chip">
                {signal}
              </span>
            ))}
            {(metrics.flakyWorkflows || []).map((workflow) => (
              <span key={workflow} className="operational-health__signal-chip operational-health__signal-chip--warn">
                Flaky workflow: {workflow}
              </span>
            ))}
          </div>
        </section>
      ) : null}

      <section className="operational-health__meta-grid">
        {triggerEntries.length > 0 && (
          <article className="operational-health__section operational-health__section--card">
            <Eyebrow>Triggers</Eyebrow>
            <div className="operational-health__triggers">
              {triggerEntries.map(([event, count]) => (
                <span key={event}>
                  {triggerLabel(event)} {count}
                </span>
              ))}
            </div>
          </article>
        )}

        {branchEntries.length > 0 && (
          <article className="operational-health__section operational-health__section--card">
            <Eyebrow>Failures by branch</Eyebrow>
            <div className="operational-health__branches">
              {branchEntries.map(([branch, health]) => {
                const failureRate = Math.round(health.failureRate * 100);
                return (
                  <div className="operational-health__branch-row" key={branch}>
                    <span className="operational-health__branch-name">{branch}</span>
                    <span
                      className={`operational-health__branch-stats ${failureRateClassName(health)}`}
                    >
                      {health.failureCount} / {health.totalRuns} ({failureRate}%)
                    </span>
                  </div>
                );
              })}
            </div>
          </article>
        )}
      </section>

      {trend && (
        <div
          className={`operational-health__trend operational-health__trend--${metrics.durationTrend}`}
        >
          {trend.icon}
          <strong>{trend.label}</strong>
        </div>
      )}

      {(lastRun || showLastSuccess) && (
        <footer className="operational-health__footer">
          {lastRun && <span>Last run: {lastRun}</span>}
          {showLastSuccess && lastSuccess && (
            <span>Last successful run: {lastSuccess}</span>
          )}
        </footer>
      )}

      {metrics.recentRuns.length > 0 && (
        <section className="operational-health__section">
          <div className="operational-health__recent-header">
            <Eyebrow>Recent runs</Eyebrow>
            <span>{filteredRuns.length} shown</span>
          </div>

          <div className="operational-health__filters">
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value={DEFAULT_FILTER}>All status</option>
              <option value="success">Success</option>
              <option value="failure">Failure</option>
              <option value="timed_out">Timed out</option>
              <option value="cancelled">Cancelled</option>
            </select>
            <select value={branchFilter} onChange={(event) => setBranchFilter(event.target.value)}>
              <option value={DEFAULT_FILTER}>All branches</option>
              {branchOptions.map((branch) => (
                <option key={branch} value={branch}>
                  {branch}
                </option>
              ))}
            </select>
            <select value={triggerFilter} onChange={(event) => setTriggerFilter(event.target.value)}>
              <option value={DEFAULT_FILTER}>All triggers</option>
              {triggerOptions.map((trigger) => (
                <option key={trigger} value={trigger}>
                  {triggerLabel(trigger)}
                </option>
              ))}
            </select>
            <select value={workflowFilter} onChange={(event) => setWorkflowFilter(event.target.value)}>
              <option value={DEFAULT_FILTER}>All workflows</option>
              {workflowOptions.map((workflow) => (
                <option key={workflow} value={workflow}>
                  {workflow}
                </option>
              ))}
            </select>
          </div>

          <div className="operational-health__runs">
            {visibleRuns.map((run) => {
              const runUrl = githubRunUrl(owner, repo, run.id);
              return (
                <a
                  className="operational-health__run-link"
                  href={runUrl}
                  key={run.id}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <span className="operational-health__run-main">
                    {runStatusIcon(run)}
                    <span className="operational-health__run-copy">
                      <strong>{run.displayName}</strong>
                      <span>
                        {run.workflowName || "workflow"} · {run.branch || "unknown branch"} ·{" "}
                        {triggerLabel(run.event || "unknown")} · {formatRelativeTime(run.startedAt)} ·{" "}
                        {formatDuration(run.durationSec)}
                      </span>
                    </span>
                  </span>
                  <ExternalLink size={14} />
                </a>
              );
            })}
          </div>

          {hasMoreRuns ? (
            <button
              className="button button--ghost button--small"
              onClick={() => setShowAllRuns((current) => !current)}
              type="button"
            >
              {showAllRuns ? "Show less" : "Show more"}
            </button>
          ) : null}
        </section>
      )}
    </Card>
  );
}

function triggerLabel(event: string): string {
  if (event === "workflow_dispatch") {
    return "manual";
  }
  return event;
}

function formatSignedPercent(value: number): string {
  const rounded = Math.round(value * 100);
  if (rounded > 0) return `+${rounded}%`;
  if (rounded < 0) return `${rounded}%`;
  return "0%";
}

function formatSignedCount(value: number): string {
  if (value > 0) return `+${value}`;
  return `${value}`;
}

function formatSignedDuration(seconds: number): string {
  if (seconds === 0) return "0s";
  const sign = seconds > 0 ? "+" : "-";
  return `${sign}${formatDuration(Math.abs(seconds))}`;
}

function failureRateClassName(health: BranchHealth): string {
  if (health.failureRate >= 0.2) {
    return "operational-health__branch-stats--high";
  }
  if (health.failureRate >= 0.1) {
    return "operational-health__branch-stats--medium";
  }
  return "";
}

function githubRunUrl(owner: string, repo: string, runId: number): string {
  return `https://github.com/${owner}/${repo}/actions/runs/${runId}`;
}

function runStatusIcon(run: RecentRunSummary): ReactNode {
  switch (run.conclusion) {
    case "success":
      return (
        <CheckCircle2
          className="operational-health__run-icon operational-health__run-icon--success"
          size={16}
        />
      );
    case "failure":
      return (
        <XCircle
          className="operational-health__run-icon operational-health__run-icon--failure"
          size={16}
        />
      );
    default:
      return (
        <MinusCircle
          className="operational-health__run-icon operational-health__run-icon--muted"
          size={16}
        />
      );
  }
}

function trendLabel(value: string): { icon: ReactNode; label: string } | null {
  switch (value) {
    case "improving":
      return { icon: <TrendingUp size={16} />, label: "Duration trend: improving" };
    case "stable":
      return { icon: <ArrowRight size={16} />, label: "Duration trend: stable" };
    case "degrading":
      return { icon: <TrendingDown size={16} />, label: "Duration trend: degrading" };
    default:
      return null;
  }
}

type StatusCardProps = {
  message: string;
  status: WorkflowMetricsResult["status"];
  workspaceId: string | null;
};

function OperationalHealthStatus({ message, status, workspaceId }: StatusCardProps) {
  if (status === "RATE_LIMITED") {
    return (
      <Card as="section" className="operational-health operational-health--note">
        <span className="operational-health__note">{message}</span>
      </Card>
    );
  }

  if (
    status === "WORKFLOW_NOT_FOUND" ||
    status === "NO_RUNS_IN_WINDOW" ||
    status === "PROVIDER_ERROR"
  ) {
    return (
      <Card as="section" className="operational-health operational-health--note">
        <span className="operational-health__note operational-health__note--muted">
          {message}
        </span>
      </Card>
    );
  }

  const reconnectUrl = connectProviderUrl("github", {
    workspaceId,
    returnTo: "/dashboard",
  });

  return (
    <Card as="section" className="operational-health operational-health--cta">
      <div className="operational-health__cta">
        <AlertTriangle aria-hidden="true" size={18} />
        <div>
          <strong>Operational health unavailable</strong>
          <p>{message}</p>
        </div>
      </div>
      <a className="button button--primary button--small" href={reconnectUrl}>
        Reconnect GitHub
      </a>
    </Card>
  );
}
