package com.scaffy.backend.repository;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

public record PersistedRepositoryAnalysis(
		RepositoryAnalysisSummary summary,
		String workflowContent,
		AnalysisResponse analysis,
		WorkflowMetricsResult workflowMetrics) {
}
