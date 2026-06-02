import type { ReactNode } from "react";
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
  SCOPE_INSUFFICIENT: "Permission missing — reconnect with required scopes.",
  RATE_LIMITED: "GitHub rate limit hit. Try again later.",
  WORKFLOW_NOT_FOUND: "Workflow file not found in GitHub.",
  NO_RUNS_IN_WINDOW: "No runs in the last 30 days.",
  PROVIDER_ERROR: "Runtime metrics temporarily unavailable.",
};

export function OperationalHealthCard({ owner, repo, result }: Props) {
  const { activeWorkspace } = useWorkspace();
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
  const recentFailures = metrics.recentFailures7d;
  const triggerEntries = Object.entries(metrics.triggerDistribution);
  const branchEntries = Object.entries(metrics.branchBreakdown);
  const lastRun = metrics.lastRunAt ? formatRelativeTime(metrics.lastRunAt) : null;
  const lastSuccess = metrics.lastSuccessAt
    ? formatRelativeTime(metrics.lastSuccessAt)
    : null;
  const showLastSuccess =
    lastSuccess && metrics.lastSuccessAt !== metrics.lastRunAt;

  return (
    <Card as="section" className="operational-health">
      <header className="operational-health__header">
        <Eyebrow>Operational health</Eyebrow>
        <div className="operational-health__title">
          <h4>Last {metrics.windowDays} days</h4>
          <span>
            Based on {metrics.totalRuns} workflow runs ({metrics.successCount} success / {metrics.failureCount} failed)
          </span>
        </div>
      </header>

      <div className="operational-health__primary">
        <div className="operational-health__rate">
          <span>Success rate:</span>
          <strong style={{ color: successRateColor(metrics.successRate) }}>
            {successRate}%
          </strong>
        </div>
        {recentFailures > 0 && (
          <div className="operational-health__failures">
            <span>Failures (7d):</span>
            <strong>{recentFailures}</strong>
          </div>
        )}
      </div>

      <div className="operational-health__secondary">
        <div>
          <span>Median duration:</span>
          <strong>{formatDuration(metrics.medianDurationSec)}</strong>
        </div>
        <div>
          <span>p95 duration:</span>
          <strong>{formatDuration(metrics.p95DurationSec)}</strong>
        </div>
        {metrics.deployStability !== null &&
          metrics.deployStability !== undefined && (
          <div>
            <span>Deploy stability:</span>
            <strong>{Math.round(metrics.deployStability * 100)}%</strong>
          </div>
        )}
      </div>

      {triggerEntries.length > 0 && (
        <section className="operational-health__section">
          <Eyebrow>Triggers</Eyebrow>
          <div className="operational-health__triggers">
            {triggerEntries.map(([event, count]) => (
              <span key={event}>
                {triggerLabel(event)} {count}
              </span>
            ))}
          </div>
        </section>
      )}

      {branchEntries.length > 0 && (
        <section className="operational-health__section">
          <Eyebrow>Failures By Branch</Eyebrow>
          <div className="operational-health__branches">
            {branchEntries.map(([branch, health]) => {
              const failureRate = Math.round(health.failureRate * 100)
              return (
                <div className="operational-health__branch-row" key={branch}>
                  <span className="operational-health__branch-name">{branch}</span>
                  <span
                    className={`operational-health__branch-stats ${failureRateClassName(health)}`}
                  >
                    {health.failureCount} / {health.totalRuns} runs ({failureRate}%)
                  </span>
                </div>
              )
            })}
          </div>
        </section>
      )}

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
          <Eyebrow>Recent Runs</Eyebrow>
          <div className="operational-health__runs">
            {metrics.recentRuns.map((run) => {
              const runUrl = githubRunUrl(owner, repo, run.id)
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
                        {run.branch || "unknown branch"} · {formatRelativeTime(run.startedAt)} · {formatDuration(run.durationSec)}
                      </span>
                    </span>
                  </span>
                  <ExternalLink size={14} />
                </a>
              )
            })}
          </div>
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
      return { icon: <TrendingUp size={16} />, label: "Improving" };
    case "stable":
      return { icon: <ArrowRight size={16} />, label: "Stable" };
    case "degrading":
      return { icon: <TrendingDown size={16} />, label: "Degrading" };
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

  const reconnectUrl = connectProviderUrl("github-repos", {
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
