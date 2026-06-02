package com.scaffy.backend.repository;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

/**
 * Wrapper persisted to the {@code analysis_json} column as of issue #70.
 *
 * <p>Pre-#70 rows contain a bare {@link AnalysisResponse} JSON object. The
 * deserializer in {@link RepositoryAnalysisRepository} detects the format by
 * checking for the {@code "analysis"} key and falls back to
 * {@link #legacy(AnalysisResponse)} for old rows — no Flyway migration needed.
 *
 * <p>{@code workflowMetrics} is {@code null} for rows written before this
 * field was introduced; Jackson silently ignores the absent key on read.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersistedAnalysisBlob(
        AnalysisResponse analysis,
        WorkflowMetricsResult workflowMetrics) {

    public PersistedAnalysisBlob {
        Objects.requireNonNull(analysis, "analysis must not be null");
    }

    /** Creates a blob for a freshly-run analysis that includes runtime metrics. */
    public static PersistedAnalysisBlob of(AnalysisResponse analysis, WorkflowMetricsResult workflowMetrics) {
        return new PersistedAnalysisBlob(analysis, workflowMetrics);
    }

    /**
     * Creates a blob with no runtime metrics.
     * Used when deserializing pre-#70 rows or when metrics are unavailable.
     */
    public static PersistedAnalysisBlob legacy(AnalysisResponse analysis) {
        return new PersistedAnalysisBlob(analysis, null);
    }
}
