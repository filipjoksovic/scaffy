package com.scaffy.backend.repository.metrics;

/**
 * Contract for provider-specific workflow runtime metrics fetchers.
 * Implementations are matched to a {@code RepositoryConnection} by
 * comparing {@link #provider()} against {@code RepositoryConnection.provider()}.
 */
public interface WorkflowMetricsProvider {

    /**
     * @return provider identifier (e.g., {@code "github-actions"}) used to match
     * against {@code RepositoryConnection.provider()}
     */
    String provider();

    /**
     * Fetches and aggregates workflow runtime metrics for the given request.
     *
     * <p>Implementations MUST NOT throw on expected failure modes (missing token,
     * rate limit, expired token, etc.). Instead, return a {@link WorkflowMetricsResult}
     * with the appropriate {@link MetricsStatus}.
     *
     * <p>Only throw {@link RuntimeException} for truly unexpected errors
     * (programming errors, JVM issues).
     */
    WorkflowMetricsResult fetchMetrics(MetricsRequest request);

}
