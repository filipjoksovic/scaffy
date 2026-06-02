package com.scaffy.backend.repository.metrics.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

/**
 * Thin Caffeine wrapper exposing a domain-specific API for workflow metrics caching.
 * TTL: 1 hour. Max size: 1000 entries.
 * Stats are recorded for diagnostics (e.g., via /actuator/metrics).
 */
@Component
public class WorkflowMetricsCache {

    private final Cache<String, WorkflowMetricsResult> cache;

    public WorkflowMetricsCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(1000)
                .recordStats()
                .build();
    }

    /**
     * Returns the cached result for the given request, or empty on a cache miss.
     */
    public Optional<WorkflowMetricsResult> get(MetricsRequest request) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(request)));
    }

    /**
     * Stores a result in the cache. The caller is responsible for deciding
     * whether the result should be cached (only AVAILABLE results should be stored).
     */
    public void put(MetricsRequest request, WorkflowMetricsResult result) {
        cache.put(buildKey(request), result);
    }

    /**
     * Removes the cached entry for the given request.
     * Useful for manual "refresh" actions.
     */
    public void invalidate(MetricsRequest request) {
        cache.invalidate(buildKey(request));
    }

    /**
     * Returns Caffeine cache stats for diagnostics.
     */
    public CacheStats stats() {
        return cache.stats();
    }

    /**
     * Builds a stable, workspace-scoped cache key.
     * userId is intentionally excluded — metrics are objective repo data,
     * shareable across workspace members.
     *
     * <p>Format: {@code {provider}|{providerInstance}|{workspaceId}|{owner}/{repo}|{workflowFile}|{windowDays}d}
     * <p>Example: {@code github-actions||550e8400-e29b-41d4-a716-446655440000|filipjoksovic/scaffy|ci.yml|30d}
     */
    private static String buildKey(MetricsRequest request) {
        return String.join("|",
                request.provider(),
                request.providerInstance() == null ? "" : request.providerInstance(),
                request.workspaceId().toString(),
                request.owner() + "/" + request.repo(),
                request.workflowFile(),
                request.windowDays() + "d"
        );
    }
}
