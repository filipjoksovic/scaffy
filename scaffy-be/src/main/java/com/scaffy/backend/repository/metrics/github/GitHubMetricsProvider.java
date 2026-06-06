package com.scaffy.backend.repository.metrics.github;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import com.scaffy.backend.repository.metrics.RecentRunSummary;
import com.scaffy.backend.repository.metrics.WorkflowMetrics;
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
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        List<WorkflowRun> filtered = allRuns.stream()
                .filter(r -> r.runStartedAt() != null && r.runStartedAt().isAfter(windowStart))
                .toList();

        if (filtered.isEmpty()) {
            return WorkflowMetrics.empty(windowDays, PROVIDER);
        }

        int totalRuns = filtered.size();
        long successCount = filtered.stream()
                .filter(r -> "success".equals(r.conclusion()))
                .count();
        long failureCount = filtered.stream()
                .filter(r -> "failure".equals(r.conclusion()))
                .count();
        double successRate = (double) successCount / totalRuns;
        double failureRate = (double) failureCount / totalRuns;

        long recentFailures7d = filtered.stream()
                .filter(r -> r.runStartedAt().isAfter(sevenDaysAgo))
                .filter(r -> "failure".equals(r.conclusion()))
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
                .limit(5)
                .map(this::toRecentRunSummary)
                .toList();

        Map<String, Integer> triggerDistribution = filtered.stream()
                .filter(r -> r.event() != null && !r.event().isBlank())
                .collect(Collectors.groupingBy(
                        WorkflowRun::event,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<String, BranchHealth> branchBreakdown = branchBreakdown(filtered);

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
                branchBreakdown);
    }

    private RecentRunSummary toRecentRunSummary(WorkflowRun run) {
        return new RecentRunSummary(
                run.id(),
                run.name(),
                run.headBranch(),
                run.conclusion(),
                Duration.between(run.runStartedAt(), run.updatedAt()).getSeconds(),
                run.runStartedAt());
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
