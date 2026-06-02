package com.scaffy.backend.repository.metrics.cache;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.MetricsStatus;
import com.scaffy.backend.repository.metrics.WorkflowMetricsProvider;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

/**
 * Decorator that adds transparent Caffeine caching to any {@link WorkflowMetricsProvider}.
 *
 * <p>Only {@link MetricsStatus#AVAILABLE} results are cached. Transient failure statuses
 * (TOKEN_MISSING, RATE_LIMITED, etc.) are passed through without caching so that the next
 * call re-checks the underlying condition (e.g., the user may reconnect within seconds).
 *
 * <p>This class is NOT a Spring {@code @Component} — it is instantiated explicitly by
 * {@link com.scaffy.backend.repository.metrics.WorkflowMetricsConfiguration}.
 */
public class CachingWorkflowMetricsProvider implements WorkflowMetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingWorkflowMetricsProvider.class);

    private final WorkflowMetricsProvider delegate;
    private final WorkflowMetricsCache cache;

    public CachingWorkflowMetricsProvider(WorkflowMetricsProvider delegate, WorkflowMetricsCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    @Override
    public WorkflowMetricsResult fetchMetrics(MetricsRequest request) {
        Optional<WorkflowMetricsResult> cached = cache.get(request);
        if (cached.isPresent()) {
            log.debug("Cache hit for {}", buildLogKey(request));
            return cached.get();
        }

        WorkflowMetricsResult fresh = delegate.fetchMetrics(request);

        if (fresh.status() == MetricsStatus.AVAILABLE) {
            cache.put(request, fresh);
        }

        return fresh;
    }

    /**
     * Returns a log-safe key — owner/repo only, no UUIDs or workflow paths.
     */
    private static String buildLogKey(MetricsRequest request) {
        return request.owner() + "/" + request.repo();
    }
}
