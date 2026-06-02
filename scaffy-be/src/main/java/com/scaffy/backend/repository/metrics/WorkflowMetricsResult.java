package com.scaffy.backend.repository.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Container for the result of a metrics fetch operation, combining a status code,
 * the aggregated metrics (when available), and an optional human-readable message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowMetricsResult(
        MetricsStatus status,
        WorkflowMetrics metrics,
        String message) {

    /** Returns a successful result with the given metrics. */
    public static WorkflowMetricsResult available(WorkflowMetrics metrics) {
        return new WorkflowMetricsResult(MetricsStatus.AVAILABLE, metrics, null);
    }

    /**
     * Returns a failed result with the given status and an optional detail message.
     * {@code status} must not be {@link MetricsStatus#AVAILABLE}.
     */
    public static WorkflowMetricsResult unavailable(MetricsStatus status, String message) {
        return new WorkflowMetricsResult(status, null, message);
    }

    /** Returns an unsupported result for providers that do not implement runtime metrics. */
    public static WorkflowMetricsResult unsupported() {
        return new WorkflowMetricsResult(MetricsStatus.UNSUPPORTED, null, null);
    }

}
