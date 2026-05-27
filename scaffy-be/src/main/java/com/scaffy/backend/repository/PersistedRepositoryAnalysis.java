package com.scaffy.backend.repository;

import com.scaffy.backend.analyze.AnalysisResponse;

public record PersistedRepositoryAnalysis(
		RepositoryAnalysisSummary summary,
		String workflowContent,
		AnalysisResponse analysis) {
}
