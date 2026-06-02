package com.scaffy.backend.repository.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated runtime metrics for a single workflow over an observation window.
 * All rate and duration fields are pre-computed by the aggregator, not the provider.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowMetrics(
        int totalRuns,
        int successCount,
        int failureCount,
        double successRate,
        double failureRate,
        int recentFailures7d,
        long medianDurationSec,
        long p95DurationSec,
        Double deployStability,
        String durationTrend,
        Instant lastRunAt,
        Instant lastSuccessAt,
        int windowDays,
        String source,
        List<RecentRunSummary> recentRuns,
        Map<String, Integer> triggerDistribution,
        Map<String, BranchHealth> branchBreakdown) {

    /**
     * Returns an empty metrics instance for the {@link MetricsStatus#NO_RUNS_IN_WINDOW} case.
     * All numeric fields are zeroed; {@code durationTrend} is {@code "insufficient_data"}.
     */
    public static WorkflowMetrics empty(int windowDays, String source) {
        return new WorkflowMetrics(
                0, 0, 0,
                0.0, 0.0,
                0, 0L, 0L,
                null,
                "insufficient_data",
                null, null,
                windowDays, source,
                List.of(),
                Map.of(),
                Map.of());
    }

}
