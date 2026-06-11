import { useMemo, useState, type ReactNode } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  Cell,
  PolarAngleAxis,
  RadialBar,
  RadialBarChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
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
  WorkflowMetrics,
  WorkflowMetricsResult,
} from "../api/repositories";
import { connectProviderUrl } from "../api/auth";
import { useWorkspace } from "../lib/workspace";
import { formatDuration, successRateColor } from "../lib/metrics";
import { formatRelativeTime } from "../lib/time";
import { Card, Eyebrow, Select } from "./index";

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
const STATUS_FILTER_OPTIONS = [
  { label: "All status", value: DEFAULT_FILTER },
  { label: "Success", value: "success" },
  { label: "Failure", value: "failure" },
  { label: "Timed out", value: "timed_out" },
  { label: "Cancelled", value: "cancelled" },
];

// Palette for trigger distribution slices, drawn from the design tokens.
const TRIGGER_COLORS = [
  "var(--color-primary)",
  "var(--color-timeline-read)",
  "var(--color-timeline-grep)",
  "var(--color-timeline-edit)",
  "var(--color-timeline-done)",
  "var(--color-timeline-thinking)",
];

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

  return <OperationalHealthMetrics metrics={metrics} owner={owner} repo={repo} />;
}

type MetricsViewProps = {
  owner: string;
  repo: string;
  metrics: WorkflowMetrics;
};

function OperationalHealthMetrics({ owner, repo, metrics }: MetricsViewProps) {
  const [statusFilter, setStatusFilter] = useState(DEFAULT_FILTER);
  const [branchFilter, setBranchFilter] = useState(DEFAULT_FILTER);
  const [triggerFilter, setTriggerFilter] = useState(DEFAULT_FILTER);
  const [workflowFilter, setWorkflowFilter] = useState(DEFAULT_FILTER);
  const [showAllRuns, setShowAllRuns] = useState(false);

  const successRate = Math.round(metrics.successRate * 100);
  const trend = trendLabel(metrics.durationTrend);
  const triggerEntries = Object.entries(metrics.triggerDistribution);
  const branchEntries = Object.entries(metrics.branchBreakdown);
  const lastRun = metrics.lastRunAt
    ? formatRelativeTime(metrics.lastRunAt)
    : null;
  const lastSuccess = metrics.lastSuccessAt
    ? formatRelativeTime(metrics.lastSuccessAt)
    : null;
  const showLastSuccess =
    lastSuccess && metrics.lastSuccessAt !== metrics.lastRunAt;

  // Oldest → newest so the duration chart reads left-to-right like a timeline.
  const runHistory = useMemo(() => {
    return metrics.recentRuns
      .filter((run) => Number.isFinite(run.durationSec))
      .slice()
      .sort(
        (a, b) =>
          new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime(),
      )
      .map((run, index) => ({
        index: index + 1,
        duration: Math.max(0, run.durationSec),
        conclusion: run.conclusion,
        name: run.displayName,
        workflow: run.workflowName,
        when: formatRelativeTime(run.startedAt),
      }));
  }, [metrics.recentRuns]);

  // Compact pass/fail sequence shown above the chart.
  const outcomeStrip = useMemo(
    () =>
      metrics.recentRuns
        .slice()
        .sort(
          (a, b) =>
            new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime(),
        )
        .slice(-24),
    [metrics.recentRuns],
  );

  const triggerData = useMemo(
    () =>
      triggerEntries
        .map(([event, count]) => ({ name: triggerLabel(event), value: count }))
        .sort((a, b) => b.value - a.value),
    [triggerEntries],
  );
  const triggerTotal = triggerData.reduce((sum, item) => sum + item.value, 0);

  const branchData = useMemo(
    () =>
      branchEntries
        .map(([branch, health]) => ({
          branch,
          failureRate: Math.round(health.failureRate * 100),
          failureCount: health.failureCount,
          totalRuns: health.totalRuns,
          tone: failureRateTone(health),
        }))
        .sort((a, b) => b.failureRate - a.failureRate),
    [branchEntries],
  );

  const failureReasons = metrics.topFailureReasons ?? [];

  const branchOptions = useMemo(
    () =>
      Array.from(
        new Set(
          metrics.recentRuns
            .map((run) => run.branch)
            .filter((branch): branch is string => !!branch),
        ),
      ).sort(),
    [metrics.recentRuns],
  );

  const triggerOptions = useMemo(
    () =>
      Array.from(
        new Set(
          metrics.recentRuns
            .map((run) => run.event)
            .filter((event): event is string => !!event),
        ),
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
        workflowFilter === DEFAULT_FILTER ||
        run.workflowName === workflowFilter;
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
  const gaugeColor = successRateColor(metrics.successRate);

  return (
    <Card as="section" className="operational-health">
      <header className="operational-health__header operational-health__header--compact">
        <div className="operational-health__title-row">
          <div className="operational-health__title">
            <Eyebrow>Operational health</Eyebrow>
            <h4>Workflow reliability · last {metrics.windowDays} days</h4>
          </div>
          {metrics.riskSummary ? (
            <span
              className={`operational-health__risk-badge operational-health__risk-badge--${metrics.riskSummary.level}`}
              title={metrics.riskSummary.reason}
            >
              {metrics.riskSummary.label}
            </span>
          ) : null}
        </div>
        <span>
          {metrics.totalRuns} runs analysed · {metrics.successCount} passed ·{" "}
          {metrics.failureCount} failed
        </span>
      </header>

      {/* Hero: success-rate gauge alongside the headline stats. */}
      <section className="operational-health__hero">
        <div className="operational-health__gauge">
          <ResponsiveContainer height={150} width="100%">
            <RadialBarChart
              barSize={12}
              data={[{ name: "success", value: successRate }]}
              endAngle={-270}
              innerRadius="74%"
              outerRadius="100%"
              startAngle={90}
            >
              <PolarAngleAxis
                angleAxisId={0}
                domain={[0, 100]}
                tick={false}
                type="number"
              />
              <RadialBar
                angleAxisId={0}
                background={{ fill: "var(--color-surface-strong)" }}
                cornerRadius={10}
                dataKey="value"
                fill={gaugeColor}
              />
            </RadialBarChart>
          </ResponsiveContainer>
          <div className="operational-health__gauge-center">
            <strong style={{ color: gaugeColor }}>{successRate}%</strong>
            <span>success rate</span>
          </div>
        </div>

        <div className="operational-health__hero-stats">
          <StatTile
            label="Failures (7d)"
            tone={metrics.recentFailures7d > 0 ? "danger" : "default"}
            value={String(metrics.recentFailures7d)}
          />
          <StatTile
            label="Median duration"
            value={formatDuration(metrics.medianDurationSec)}
          />
          <StatTile
            label="p95 duration"
            value={formatDuration(metrics.p95DurationSec)}
          />
          <StatTile
            hint={lastRun ? `Last run ${lastRun}` : undefined}
            label="Total runs"
            value={String(metrics.totalRuns)}
          />
          {trend ? (
            <div
              className={`operational-health__hero-trend operational-health__trend--${metrics.durationTrend}`}
            >
              {trend.icon}
              <span>{trend.label}</span>
            </div>
          ) : null}
        </div>
      </section>

      {/* Duration timeline across the window. */}
      {runHistory.length >= 2 ? (
        <section className="operational-health__chart-card">
          <div className="operational-health__chart-head">
            <Eyebrow>Run duration timeline</Eyebrow>
            <div className="operational-health__legend">
              <LegendDot color="var(--color-success)" label="passed" />
              <LegendDot color="var(--color-error)" label="failed" />
              <LegendDot color="var(--color-muted)" label="other" />
            </div>
          </div>
          {outcomeStrip.length > 0 ? (
            <div
              aria-label="Recent run outcomes, oldest to newest"
              className="operational-health__outcome-strip"
            >
              {outcomeStrip.map((run) => (
                <span
                  className={`operational-health__outcome-cell operational-health__outcome-cell--${outcomeTone(run.conclusion)}`}
                  key={run.id}
                  title={`${run.displayName} · ${run.conclusion ?? "unknown"} · ${formatRelativeTime(run.startedAt)}`}
                />
              ))}
            </div>
          ) : null}
          <div className="operational-health__chart-body">
            <ResponsiveContainer height={180} width="100%">
              <AreaChart
                data={runHistory}
                margin={{ top: 8, right: 8, bottom: 0, left: -16 }}
              >
                <defs>
                  <linearGradient
                    id="oh-duration-fill"
                    x1="0"
                    x2="0"
                    y1="0"
                    y2="1"
                  >
                    <stop
                      offset="0%"
                      stopColor="var(--color-primary)"
                      stopOpacity={0.28}
                    />
                    <stop
                      offset="100%"
                      stopColor="var(--color-primary)"
                      stopOpacity={0.02}
                    />
                  </linearGradient>
                </defs>
                <XAxis
                  axisLine={false}
                  dataKey="index"
                  tick={{ fill: "var(--color-muted)", fontSize: 11 }}
                  tickLine={false}
                />
                <YAxis
                  axisLine={false}
                  tick={{ fill: "var(--color-muted)", fontSize: 11 }}
                  tickFormatter={(value: number) => formatDuration(value)}
                  tickLine={false}
                  width={56}
                />
                <Tooltip
                  content={<DurationTooltip />}
                  cursor={{ stroke: "var(--color-hairline-strong)" }}
                />
                {metrics.medianDurationSec > 0 ? (
                  <ReferenceLine
                    label={{
                      fill: "var(--color-muted)",
                      fontSize: 10,
                      position: "insideTopRight",
                      value: "median",
                    }}
                    stroke="var(--color-hairline-strong)"
                    strokeDasharray="4 4"
                    y={metrics.medianDurationSec}
                  />
                ) : null}
                <Area
                  activeDot={{ r: 4 }}
                  dataKey="duration"
                  dot={<OutcomeDot />}
                  fill="url(#oh-duration-fill)"
                  stroke="var(--color-primary)"
                  strokeWidth={2}
                  type="monotone"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </section>
      ) : null}

      {/* Distribution charts: triggers + branch reliability. */}
      <section className="operational-health__charts-grid">
        {triggerData.length > 0 ? (
          <article className="operational-health__chart-card">
            <Eyebrow>Triggers</Eyebrow>
            <div className="operational-health__bars">
              {triggerData.map((item, index) => {
                const share = triggerTotal
                  ? Math.round((item.value / triggerTotal) * 100)
                  : 0;
                const color = TRIGGER_COLORS[index % TRIGGER_COLORS.length];
                return (
                  <div className="operational-health__bar-row" key={item.name}>
                    <span className="operational-health__bar-label">
                      {item.name}
                    </span>
                    <div className="operational-health__bar-track">
                      <div
                        className="operational-health__bar-fill"
                        style={{ background: color, width: `${share}%` }}
                      />
                    </div>
                    <span className="operational-health__bar-value">
                      {item.value}
                    </span>
                  </div>
                );
              })}
            </div>
          </article>
        ) : null}

        {branchData.length > 0 ? (
          <article className="operational-health__chart-card">
            <div className="operational-health__chart-head">
              <Eyebrow>Failure rate by branch</Eyebrow>
              <span className="operational-health__chart-hint">% of runs</span>
            </div>
            <div className="operational-health__chart-body">
              <ResponsiveContainer
                height={Math.max(120, branchData.length * 38)}
                width="100%"
              >
                <BarChart
                  barCategoryGap="28%"
                  data={branchData}
                  layout="vertical"
                  margin={{ top: 0, right: 36, bottom: 0, left: 0 }}
                >
                  <XAxis domain={[0, 100]} hide type="number" />
                  <YAxis
                    axisLine={false}
                    dataKey="branch"
                    tick={{ fill: "var(--color-body)", fontSize: 12 }}
                    tickLine={false}
                    type="category"
                    width={110}
                  />
                  <Tooltip
                    content={<BranchTooltip />}
                    cursor={{ fill: "var(--color-hairline-soft)" }}
                  />
                  <Bar
                    background={{
                      fill: "var(--color-surface-strong)",
                      radius: 4,
                    }}
                    dataKey="failureRate"
                    label={{
                      fill: "var(--color-muted)",
                      fontSize: 11,
                      formatter: (value: unknown) => `${value}%`,
                      position: "right",
                    }}
                    radius={4}
                  >
                    {branchData.map((row) => (
                      <Cell fill={toneColor(row.tone)} key={row.branch} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </article>
        ) : null}
      </section>

      {/* Narrative insights. */}
      {metrics.nextBestAction || metrics.periodDelta ? (
        <section className="operational-health__insights">
          {metrics.nextBestAction ? (
            <article
              className={`operational-health__next-action operational-health__next-action--${metrics.nextBestAction.severity}`}
            >
              <Eyebrow>Next best action</Eyebrow>
              <strong>{metrics.nextBestAction.title}</strong>
              <p>{metrics.nextBestAction.detail}</p>
            </article>
          ) : null}

          {metrics.periodDelta ? (
            <article className="operational-health__delta">
              <Eyebrow>30d vs previous 30d</Eyebrow>
              <div className="operational-health__delta-grid">
                <DeltaStat
                  good={metrics.periodDelta.successRateDelta >= 0}
                  label="Success"
                  value={formatSignedPercent(
                    metrics.periodDelta.successRateDelta,
                  )}
                />
                <DeltaStat
                  good={metrics.periodDelta.failureCountDelta <= 0}
                  label="Failures"
                  value={formatSignedCount(
                    metrics.periodDelta.failureCountDelta,
                  )}
                />
                <DeltaStat
                  good={metrics.periodDelta.medianDurationDeltaSec <= 0}
                  label="Median"
                  value={formatSignedDuration(
                    metrics.periodDelta.medianDurationDeltaSec,
                  )}
                />
                <DeltaStat
                  good={metrics.periodDelta.p95DurationDeltaSec <= 0}
                  label="p95"
                  value={formatSignedDuration(
                    metrics.periodDelta.p95DurationDeltaSec,
                  )}
                />
              </div>
            </article>
          ) : null}
        </section>
      ) : null}

      {failureReasons.length > 0 ? (
        <section className="operational-health__chart-card operational-health__section--tight">
          <Eyebrow>Top failure reasons</Eyebrow>
          <div className="operational-health__bars">
            {failureReasons.map((reason) => (
              <div className="operational-health__bar-row" key={reason.reason}>
                <span
                  className="operational-health__bar-label"
                  title={reason.reason}
                >
                  {reason.reason}
                </span>
                <div className="operational-health__bar-track">
                  <div
                    className="operational-health__bar-fill operational-health__bar-fill--danger"
                    style={{ width: `${Math.round(reason.share * 100)}%` }}
                  />
                </div>
                <span className="operational-health__bar-value">
                  {reason.count}
                </span>
              </div>
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
              <span
                key={workflow}
                className="operational-health__signal-chip operational-health__signal-chip--warn"
              >
                Flaky workflow: {workflow}
              </span>
            ))}
          </div>
        </section>
      ) : null}

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
            <Select
              compact
              hideLabel
              id="recent-runs-status-filter"
              items={STATUS_FILTER_OPTIONS}
              label="Status"
              onValueChange={setStatusFilter}
              value={statusFilter}
            />
            <Select
              compact
              hideLabel
              id="recent-runs-branch-filter"
              items={[
                { label: "All branches", value: DEFAULT_FILTER },
                ...branchOptions.map((branch) => ({
                  label: branch,
                  value: branch,
                })),
              ]}
              label="Branch"
              onValueChange={setBranchFilter}
              value={branchFilter}
            />
            <Select
              compact
              hideLabel
              id="recent-runs-trigger-filter"
              items={[
                { label: "All triggers", value: DEFAULT_FILTER },
                ...triggerOptions.map((trigger) => ({
                  label: triggerLabel(trigger),
                  value: trigger,
                })),
              ]}
              label="Trigger"
              onValueChange={setTriggerFilter}
              value={triggerFilter}
            />
            <Select
              compact
              hideLabel
              id="recent-runs-workflow-filter"
              items={[
                { label: "All workflows", value: DEFAULT_FILTER },
                ...workflowOptions.map((workflow) => ({
                  label: workflow,
                  value: workflow,
                })),
              ]}
              label="Workflow"
              onValueChange={setWorkflowFilter}
              value={workflowFilter}
            />
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
                        {run.workflowName || "workflow"} ·{" "}
                        {run.branch || "unknown branch"} ·{" "}
                        {triggerLabel(run.event || "unknown")} ·{" "}
                        {formatRelativeTime(run.startedAt)} ·{" "}
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

type StatTileProps = {
  label: string;
  value: string;
  tone?: "default" | "danger";
  hint?: string;
};

function StatTile({ label, value, tone = "default", hint }: StatTileProps) {
  return (
    <div className="operational-health__stat">
      <span className="operational-health__stat-label">{label}</span>
      <strong
        className={
          tone === "danger"
            ? "operational-health__stat-value operational-health__stat-value--danger"
            : "operational-health__stat-value"
        }
      >
        {value}
      </strong>
      {hint ? (
        <span className="operational-health__stat-hint">{hint}</span>
      ) : null}
    </div>
  );
}

type DeltaStatProps = {
  label: string;
  value: string;
  good: boolean;
};

function DeltaStat({ label, value, good }: DeltaStatProps) {
  const neutral = value === "0%" || value === "0" || value === "0s";
  const cls = neutral
    ? ""
    : good
      ? "operational-health__delta-value--good"
      : "operational-health__delta-value--bad";
  return (
    <div>
      <span>{label}</span>
      <strong className={cls}>{value}</strong>
    </div>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="operational-health__legend-item">
      <span
        className="operational-health__legend-dot"
        style={{ background: color }}
      />
      {label}
    </span>
  );
}

type DotPayload = {
  conclusion: string | null;
};

// Custom dot that colours each point by its run outcome.
function OutcomeDot(props: {
  cx?: number;
  cy?: number;
  payload?: DotPayload;
}) {
  const { cx, cy, payload } = props;
  if (typeof cx !== "number" || typeof cy !== "number") return null;
  return (
    <circle
      cx={cx}
      cy={cy}
      fill={outcomeColor(payload?.conclusion ?? null)}
      r={3.5}
      stroke="var(--color-surface-card)"
      strokeWidth={1.5}
    />
  );
}

type TooltipEntry = {
  payload: {
    name?: string;
    workflow?: string | null;
    duration: number;
    conclusion: string | null;
    when: string;
  };
};

function DurationTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: TooltipEntry[];
}) {
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0].payload;
  return (
    <div className="operational-health__chart-tooltip">
      <strong>{point.name}</strong>
      <span>{point.workflow || "workflow"}</span>
      <span>
        {formatDuration(point.duration)} · {point.conclusion ?? "unknown"}
      </span>
      <span>{point.when}</span>
    </div>
  );
}

type BranchTooltipEntry = {
  payload: {
    branch: string;
    failureRate: number;
    failureCount: number;
    totalRuns: number;
  };
};

function BranchTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: BranchTooltipEntry[];
}) {
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0].payload;
  return (
    <div className="operational-health__chart-tooltip">
      <strong>{point.branch}</strong>
      <span>{point.failureRate}% failure rate</span>
      <span>
        {point.failureCount} failed / {point.totalRuns} runs
      </span>
    </div>
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

type FailureTone = "default" | "medium" | "high";

function failureRateTone(health: BranchHealth): FailureTone {
  if (health.failureRate >= 0.2) return "high";
  if (health.failureRate >= 0.1) return "medium";
  return "default";
}

function toneColor(tone: FailureTone): string {
  switch (tone) {
    case "high":
      return "var(--color-error)";
    case "medium":
      return "var(--color-timeline-done)";
    default:
      return "var(--color-success)";
  }
}

function outcomeTone(conclusion: string | null): string {
  if (conclusion === "success") return "success";
  if (conclusion === "failure") return "failure";
  return "muted";
}

function outcomeColor(conclusion: string | null): string {
  if (conclusion === "success") return "var(--color-success)";
  if (conclusion === "failure") return "var(--color-error)";
  return "var(--color-muted)";
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
      return {
        icon: <TrendingUp size={16} />,
        label: "Duration improving",
      };
    case "stable":
      return {
        icon: <ArrowRight size={16} />,
        label: "Duration stable",
      };
    case "degrading":
      return {
        icon: <TrendingDown size={16} />,
        label: "Duration degrading",
      };
    default:
      return null;
  }
}

type StatusCardProps = {
  message: string;
  status: WorkflowMetricsResult["status"];
  workspaceId: string | null;
};

function OperationalHealthStatus({
  message,
  status,
  workspaceId,
}: StatusCardProps) {
  if (status === "RATE_LIMITED") {
    return (
      <Card
        as="section"
        className="operational-health operational-health--note"
      >
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
      <Card
        as="section"
        className="operational-health operational-health--note"
      >
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
