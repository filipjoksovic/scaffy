package com.scaffy.backend.repository.metrics.github;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.scaffy.backend.auth.OAuthAccessTokenRecord;
import com.scaffy.backend.auth.ProviderTokenCrypto;
import com.scaffy.backend.auth.WorkspaceOAuthTokenRepository;
import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.MetricsStatus;
import com.scaffy.backend.repository.metrics.BranchHealth;
import com.scaffy.backend.repository.metrics.FailureReasonInsight;
import com.scaffy.backend.repository.metrics.RecentRunSummary;
import com.scaffy.backend.repository.metrics.NextBestAction;
import com.scaffy.backend.repository.metrics.OperationalRiskSummary;
import com.scaffy.backend.repository.metrics.WorkflowMetrics;
import com.scaffy.backend.repository.metrics.WorkflowPeriodDelta;
import com.scaffy.backend.repository.metrics.WorkflowMetricsProvider;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;
import com.scaffy.backend.repository.metrics.github.GitHubActionsApiClient.ApiCallOutcome;
import com.scaffy.backend.repository.metrics.github.GitHubActionsApiClient.WorkflowRun;

/**
 * {@link WorkflowMetricsProvider} for GitHub Actions.
 * Retrieves the OAuth token, delegates API calls to {@link GitHubActionsApiClient},
 * and aggregates raw run data into a {@link WorkflowMetrics} snapshot.
 */
@Component
public class GitHubMetricsProvider implements WorkflowMetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubMetricsProvider.class);
    private static final String PROVIDER = "github-actions";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 5;
    private static final Set<String> FAILURE_CONCLUSIONS = Set.of(
            "failure", "timed_out", "cancelled", "startup_failure", "action_required", "stale");

    private final GitHubActionsApiClient apiClient;
    private final WorkspaceOAuthTokenRepository tokenRepository;
    private final ProviderTokenCrypto tokenCrypto;

    public GitHubMetricsProvider(
            GitHubActionsApiClient apiClient,
            WorkspaceOAuthTokenRepository tokenRepository,
            ProviderTokenCrypto tokenCrypto) {
        this.apiClient = apiClient;
        this.tokenRepository = tokenRepository;
        this.tokenCrypto = tokenCrypto;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    /**
     * Fetches and aggregates workflow runtime metrics.
     * Never throws — all failure modes are represented as {@link WorkflowMetricsResult} statuses.
     */
    @Override
    public WorkflowMetricsResult fetchMetrics(MetricsRequest request) {
        // Tokens are stored under provider "github" (set by OAuthLoginSuccessHandler),
        // not "github-actions" (which is the metrics provider identifier).
        Optional<OAuthAccessTokenRecord> tokenRecord = tokenRepository.findToken(
                request.workspaceId(), request.userId(), "github", request.providerInstance());

        if (tokenRecord.isEmpty()) {
            return WorkflowMetricsResult.unavailable(
                    MetricsStatus.TOKEN_MISSING,
                    "Connect GitHub in this workspace to see runtime metrics.");
        }

        OAuthAccessTokenRecord token = tokenRecord.get();
        if (token.expiresAt() != null) {
            log.info(
                    "Stored GitHub OAuth token has expiry metadata userId={} expiresAt={}; deferring validity check to GitHub API",
                    request.userId(),
                    token.expiresAt());
        }

        String accessToken = tokenCrypto.decrypt(token.encryptedAccessToken());

        ApiCallOutcome outcome = apiClient.listWorkflowRuns(
                request.owner(), request.repo(), request.workflowFile(),
                accessToken, PER_PAGE, MAX_PAGES);

        return switch (outcome) {
            case ApiCallOutcome.Success s -> {
                WorkflowMetrics metrics = aggregate(s.data().runs(), request.windowDays());
                if (metrics.totalRuns() == 0) {
                    yield new WorkflowMetricsResult(
                            MetricsStatus.NO_RUNS_IN_WINDOW,
                            WorkflowMetrics.empty(request.windowDays(), PROVIDER),
                            null);
                }
                yield WorkflowMetricsResult.available(metrics);
            }
            case ApiCallOutcome.RateLimited r -> {
                String msg = Instant.EPOCH.equals(r.resetAt())
                        ? "GitHub rate limit exceeded."
                        : "GitHub rate limit exceeded. Resets at " + r.resetAt() + ".";
                yield WorkflowMetricsResult.unavailable(MetricsStatus.RATE_LIMITED, msg);
            }
            case ApiCallOutcome.Unauthorized u ->
                WorkflowMetricsResult.unavailable(
                        MetricsStatus.TOKEN_EXPIRED,
                        "GitHub token rejected. Reconnect GitHub to refresh metrics.");
            case ApiCallOutcome.Forbidden f ->
                WorkflowMetricsResult.unavailable(
                        MetricsStatus.SCOPE_INSUFFICIENT,
                        "GitHub token lacks required permissions. Reconnect with 'repo' scope.");
            case ApiCallOutcome.NotFound n ->
                WorkflowMetricsResult.unavailable(
                        MetricsStatus.WORKFLOW_NOT_FOUND,
                        "Workflow file not found in repository. Re-run analysis to refresh.");
            case ApiCallOutcome.ServerError e -> {
                log.warn("GitHub Actions API server error status={} owner={} repo={}",
                        e.status(), request.owner(), request.repo());
                yield WorkflowMetricsResult.unavailable(
                        MetricsStatus.PROVIDER_ERROR, "GitHub returned an error. Try again later.");
            }
            case ApiCallOutcome.NetworkError ne -> {
                log.warn("GitHub Actions API network error owner={} repo={}",
                        request.owner(), request.repo(), ne.cause());
                yield WorkflowMetricsResult.unavailable(
                        MetricsStatus.PROVIDER_ERROR, "Could not reach GitHub. Try again later.");
            }
        };
    }

    private WorkflowMetrics aggregate(List<WorkflowRun> allRuns, int windowDays) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(windowDays, ChronoUnit.DAYS);
                Instant previousWindowStart = windowStart.minus(windowDays, ChronoUnit.DAYS);
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        List<WorkflowRun> filtered = allRuns.stream()
                .filter(r -> r.runStartedAt() != null && r.runStartedAt().isAfter(windowStart))
                .toList();

        if (filtered.isEmpty()) {
            return WorkflowMetrics.empty(windowDays, PROVIDER);
        }

        List<WorkflowRun> previousWindow = allRuns.stream()
                .filter(r -> r.runStartedAt() != null
                        && !r.runStartedAt().isBefore(previousWindowStart)
                        && r.runStartedAt().isBefore(windowStart))
                .toList();

        int totalRuns = filtered.size();
        long successCount = filtered.stream()
                .filter(r -> "success".equals(r.conclusion()))
                .count();
        long failureCount = filtered.stream()
                .filter(this::isFailedRun)
                .count();
        double successRate = (double) successCount / totalRuns;
        double failureRate = (double) failureCount / totalRuns;

        long recentFailures7d = filtered.stream()
                .filter(r -> r.runStartedAt().isAfter(sevenDaysAgo))
                .filter(this::isFailedRun)
                .count();

        List<Long> allDurations = filtered.stream()
                .filter(r -> r.runStartedAt() != null && r.updatedAt() != null)
                .map(r -> Duration.between(r.runStartedAt(), r.updatedAt()).getSeconds())
                .filter(d -> d > 0)
                .sorted()
                .toList();

        long medianDurationSec = percentile(allDurations, 0.50);
        long p95DurationSec = percentile(allDurations, 0.95);

        Double deployStability = deployStability(filtered);
        String durationTrend = durationTrend(filtered, sevenDaysAgo, windowStart);

        Instant lastRunAt = filtered.stream()
                .map(WorkflowRun::runStartedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Instant lastSuccessAt = filtered.stream()
                .filter(r -> "success".equals(r.conclusion()))
                .map(WorkflowRun::runStartedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<RecentRunSummary> recentRuns = filtered.stream()
                .filter(r -> r.conclusion() != null)
                .filter(r -> r.runStartedAt() != null && r.updatedAt() != null)
                .sorted(Comparator.comparing(WorkflowRun::runStartedAt, Comparator.reverseOrder()))
                .limit(20)
                .map(this::toRecentRunSummary)
                .toList();

        Map<String, Integer> triggerDistribution = filtered.stream()
                .filter(r -> r.event() != null && !r.event().isBlank())
                .collect(Collectors.groupingBy(
                        WorkflowRun::event,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<String, BranchHealth> branchBreakdown = branchBreakdown(filtered);
        WorkflowPeriodDelta periodDelta = periodDelta(filtered, previousWindow);
        List<FailureReasonInsight> topFailureReasons = topFailureReasons(filtered);
        List<String> regressionSignals = regressionSignals(filtered, periodDelta, branchBreakdown);
        List<String> flakyWorkflows = flakyWorkflows(filtered);
        OperationalRiskSummary riskSummary = riskSummary(
                successRate,
                recentFailures7d,
                branchBreakdown.get("main"),
                durationTrend,
                regressionSignals,
                topFailureReasons);
        NextBestAction nextBestAction = nextBestAction(
                riskSummary,
                topFailureReasons,
                branchBreakdown,
                periodDelta,
                flakyWorkflows,
                durationTrend);

        return new WorkflowMetrics(
                totalRuns,
                (int) successCount,
                (int) failureCount,
                successRate,
                failureRate,
                (int) recentFailures7d,
                medianDurationSec,
                p95DurationSec,
                deployStability,
                durationTrend,
                lastRunAt,
                lastSuccessAt,
                windowDays,
                PROVIDER,
                recentRuns,
                triggerDistribution,
                                branchBreakdown,
                                riskSummary,
                                nextBestAction,
                                periodDelta,
                                topFailureReasons,
                                regressionSignals,
                                flakyWorkflows);
    }

    private RecentRunSummary toRecentRunSummary(WorkflowRun run) {
        return new RecentRunSummary(
                run.id(),
                run.name(),
                                run.workflowName(),
                                run.event(),
                run.headBranch(),
                run.conclusion(),
                Duration.between(run.runStartedAt(), run.updatedAt()).getSeconds(),
                run.runStartedAt());
    }

        private WorkflowPeriodDelta periodDelta(List<WorkflowRun> currentWindow, List<WorkflowRun> previousWindow) {
                WindowStats current = toWindowStats(currentWindow);
                WindowStats previous = toWindowStats(previousWindow);

                String trend;
                if (previous.totalRuns == 0) {
                        trend = "insufficient_data";
                }
                else if (current.successRate - previous.successRate >= 0.08) {
                        trend = "improving";
                }
                else if (previous.successRate - current.successRate >= 0.08) {
                        trend = "degrading";
                }
                else {
                        trend = "stable";
                }

                return new WorkflowPeriodDelta(
                                previous.successRate,
                                current.successRate,
                                current.successRate - previous.successRate,
                                previous.failureCount,
                                current.failureCount,
                                current.failureCount - previous.failureCount,
                                previous.medianDurationSec,
                                current.medianDurationSec,
                                current.medianDurationSec - previous.medianDurationSec,
                                previous.p95DurationSec,
                                current.p95DurationSec,
                                current.p95DurationSec - previous.p95DurationSec,
                                trend);
        }

        private WindowStats toWindowStats(List<WorkflowRun> runs) {
                if (runs.isEmpty()) {
                        return new WindowStats(0, 0, 0.0, 0, 0);
                }
                int totalRuns = runs.size();
                int successCount = (int) runs.stream().filter(r -> "success".equals(r.conclusion())).count();
                int failureCount = (int) runs.stream().filter(this::isFailedRun).count();
                double successRate = totalRuns == 0 ? 0.0 : (double) successCount / totalRuns;
                List<Long> durations = runs.stream()
                                .filter(r -> r.runStartedAt() != null && r.updatedAt() != null)
                                .map(r -> Duration.between(r.runStartedAt(), r.updatedAt()).getSeconds())
                                .filter(d -> d > 0)
                                .sorted()
                                .toList();
                return new WindowStats(
                                totalRuns,
                                failureCount,
                                successRate,
                                percentile(durations, 0.50),
                                percentile(durations, 0.95));
        }

        private List<FailureReasonInsight> topFailureReasons(List<WorkflowRun> runs) {
                long totalFailedRuns = runs.stream().filter(this::isFailedRun).count();
                if (totalFailedRuns == 0) {
                        return List.of();
                }

                return runs.stream()
                                .filter(this::isFailedRun)
                                .map(run -> normalizeFailureReason(run.conclusion()))
                                .collect(Collectors.groupingBy(reason -> reason, Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(3)
                                .map(entry -> new FailureReasonInsight(
                                                entry.getKey(),
                                                entry.getValue().intValue(),
                                                (double) entry.getValue() / totalFailedRuns))
                                .toList();
        }

        private List<String> regressionSignals(
                        List<WorkflowRun> currentWindow,
                        WorkflowPeriodDelta periodDelta,
                        Map<String, BranchHealth> branchBreakdown) {
                List<String> signals = new java.util.ArrayList<>();

                if (periodDelta.trend().equals("degrading")) {
                        signals.add("Success rate dropped compared to the previous window.");
                }
                if (periodDelta.medianDurationDeltaSec() > 30) {
                        signals.add("Median run duration increased by more than 30 seconds.");
                }
                if (periodDelta.failureCountDelta() >= 3) {
                        signals.add("Failure volume increased by " + periodDelta.failureCountDelta() + " runs.");
                }

                BranchHealth main = branchBreakdown.get("main");
                if (main != null && main.totalRuns() >= 4 && main.failureRate() >= 0.5) {
                        signals.add("Main branch has elevated failure rate.");
                }

                if (signals.isEmpty() && currentWindow.size() >= 8) {
                        signals.add("No clear regression detected in the current window.");
                }

                return signals.stream().limit(3).toList();
        }

        private List<String> flakyWorkflows(List<WorkflowRun> runs) {
                return runs.stream()
                                .filter(r -> r.workflowName() != null && !r.workflowName().isBlank())
                                .collect(Collectors.groupingBy(WorkflowRun::workflowName))
                                .entrySet().stream()
                                .filter(entry -> entry.getValue().size() >= 4)
                                .map(entry -> {
                                        int total = entry.getValue().size();
                                        long failures = entry.getValue().stream().filter(this::isFailedRun).count();
                                        long successes = entry.getValue().stream().filter(r -> "success".equals(r.conclusion())).count();
                                        double failureRate = total == 0 ? 0.0 : (double) failures / total;
                                        if (successes > 0 && failures > 0 && failureRate >= 0.25 && failureRate <= 0.75) {
                                                return entry.getKey();
                                        }
                                        return null;
                                })
                                .filter(name -> name != null)
                                .limit(3)
                                .toList();
        }

        private OperationalRiskSummary riskSummary(
                        double successRate,
                        long recentFailures7d,
                        BranchHealth main,
                        String durationTrend,
                        List<String> regressionSignals,
                        List<FailureReasonInsight> topFailureReasons) {
                if (successRate < 0.60
                                || recentFailures7d >= 5
                                || (main != null && main.totalRuns() >= 4 && main.failureRate() >= 0.50)) {
                        String reason = topFailureReasons.isEmpty()
                                        ? "High failure pressure in the recent window."
                                        : "Top issue: " + topFailureReasons.get(0).reason() + ".";
                        return new OperationalRiskSummary("critical", "Critical", reason);
                }

                if (successRate < 0.80
                                || recentFailures7d >= 2
                                || "degrading".equals(durationTrend)
                                || regressionSignals.stream().anyMatch(signal -> signal.toLowerCase().contains("dropped"))) {
                        return new OperationalRiskSummary(
                                        "warning",
                                        "Warning",
                                        "Quality is drifting; preventive action recommended.");
                }

                return new OperationalRiskSummary(
                                "stable",
                                "Stable",
                                "Healthy execution profile in the current window.");
        }

        private NextBestAction nextBestAction(
                        OperationalRiskSummary riskSummary,
                        List<FailureReasonInsight> topFailureReasons,
                        Map<String, BranchHealth> branchBreakdown,
                        WorkflowPeriodDelta periodDelta,
                        List<String> flakyWorkflows,
                        String durationTrend) {
                BranchHealth main = branchBreakdown.get("main");
                if (main != null && main.totalRuns() >= 4 && main.failureRate() >= 0.50) {
                        return new NextBestAction(
                                        "Stabilize main branch checks",
                                        "Main branch is failing frequently. Inspect the latest failed run and gate merges on green checks.",
                                        "high",
                                        "main");
                }

                if (!topFailureReasons.isEmpty()) {
                        FailureReasonInsight top = topFailureReasons.get(0);
                        if (top.reason().equals("Timeout")) {
                                return new NextBestAction(
                                                "Reduce timeout pressure",
                                                "Timeout is the top failure reason. Prioritize dependency cache and split long-running jobs.",
                                                "medium",
                                                "workflow");
                        }
                        return new NextBestAction(
                                        "Address top failure reason",
                                        "Most failures are caused by " + top.reason().toLowerCase() + ". Start from the newest failed run.",
                                        "medium",
                                        "workflow");
                }

                if (!flakyWorkflows.isEmpty()) {
                        return new NextBestAction(
                                        "Investigate flaky workflow",
                                        "Workflow '" + flakyWorkflows.get(0) + "' alternates between success and failure.",
                                        "medium",
                                        flakyWorkflows.get(0));
                }

                if (periodDelta.failureCountDelta() > 0 || "degrading".equals(durationTrend)) {
                        return new NextBestAction(
                                        "Review recent regressions",
                                        "Failure count or duration trend worsened versus the previous window.",
                                        "medium",
                                        "window-comparison");
                }

                return new NextBestAction(
                                "Maintain current baseline",
                                "Health is stable. Keep mandatory checks and monitor weekly trends.",
                                "low",
                                riskSummary.level());
        }

        private boolean isFailedRun(WorkflowRun run) {
                return run.conclusion() != null && FAILURE_CONCLUSIONS.contains(run.conclusion());
        }

        private String normalizeFailureReason(String conclusion) {
                if (conclusion == null || conclusion.isBlank()) {
                        return "Unknown";
                }
                return switch (conclusion) {
                        case "failure" -> "Failed checks";
                        case "timed_out" -> "Timeout";
                        case "cancelled" -> "Cancelled";
                        case "startup_failure" -> "Runner startup failure";
                        case "action_required" -> "Manual action required";
                        case "stale" -> "Stale run";
                        default -> conclusion.replace('_', ' ');
                };
        }

        private record WindowStats(
                        int totalRuns,
                        int failureCount,
                        double successRate,
                        long medianDurationSec,
                        long p95DurationSec) {
        }

    private Map<String, BranchHealth> branchBreakdown(List<WorkflowRun> runs) {
        Map<String, List<WorkflowRun>> byBranch = runs.stream()
                .filter(r -> r.headBranch() != null && !r.headBranch().isBlank())
                .collect(Collectors.groupingBy(WorkflowRun::headBranch, LinkedHashMap::new, Collectors.toList()));

        if (byBranch.isEmpty()) {
            return Map.of();
        }

        Map<String, BranchHealth> computed = byBranch.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, List<WorkflowRun>>>comparingInt(entry -> entry.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toBranchHealth(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        LinkedHashMap<String, BranchHealth> limited = computed.entrySet().stream()
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        if (!limited.containsKey("main") && computed.containsKey("main")) {
            limited.put("main", computed.get("main"));
        }

        return limited;
    }

    private BranchHealth toBranchHealth(List<WorkflowRun> runs) {
        int totalRuns = runs.size();
        int failureCount = (int) runs.stream()
                .filter(r -> "failure".equals(r.conclusion()))
                .count();
        double failureRate = totalRuns == 0 ? 0.0 : (double) failureCount / totalRuns;
        return new BranchHealth(totalRuns, failureCount, failureRate);
    }

    private Double deployStability(List<WorkflowRun> runs) {
        List<WorkflowRun> deployRuns = runs.stream()
                .filter(r -> r.workflowName() != null
                        && r.workflowName().toLowerCase().contains("deploy"))
                .toList();
        if (deployRuns.isEmpty()) {
            return null;
        }
        long deploySuccesses = deployRuns.stream()
                .filter(r -> "success".equals(r.conclusion()))
                .count();
        return (double) deploySuccesses / deployRuns.size();
    }

    private String durationTrend(List<WorkflowRun> runs, Instant sevenDaysAgo, Instant windowStart) {
        List<Long> recent = runs.stream()
                .filter(r -> r.runStartedAt() != null && r.runStartedAt().isAfter(sevenDaysAgo))
                .filter(r -> r.updatedAt() != null)
                .map(r -> Duration.between(r.runStartedAt(), r.updatedAt()).getSeconds())
                .filter(d -> d > 0)
                .sorted()
                .toList();

        List<Long> older = runs.stream()
                .filter(r -> r.runStartedAt() != null
                        && !r.runStartedAt().isAfter(sevenDaysAgo)
                        && r.runStartedAt().isAfter(windowStart))
                .filter(r -> r.updatedAt() != null)
                .map(r -> Duration.between(r.runStartedAt(), r.updatedAt()).getSeconds())
                .filter(d -> d > 0)
                .sorted()
                .toList();

        if (recent.size() < 5 || older.size() < 5) {
            return "insufficient_data";
        }

        long recentMedian = percentile(recent, 0.50);
        long olderMedian = percentile(older, 0.50);

        if (olderMedian == 0) {
            return "insufficient_data";
        }

        double ratio = (double) recentMedian / olderMedian;
        if (ratio > 1.20) {
            return "degrading";
        }
        if (ratio < 0.80) {
            return "improving";
        }
        return "stable";
    }

    private static long percentile(List<Long> sortedAsc, double p) {
        if (sortedAsc.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sortedAsc.size()) - 1;
        return sortedAsc.get(Math.max(0, Math.min(idx, sortedAsc.size() - 1)));
    }
}
