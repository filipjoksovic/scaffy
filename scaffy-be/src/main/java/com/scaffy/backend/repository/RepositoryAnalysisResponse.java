package com.scaffy.backend.repository;

import java.time.OffsetDateTime;

import com.scaffy.backend.analyze.AnalysisResponse;

public record RepositoryAnalysisResponse(
		String repositoryId,
		String repository,
		String workflowPath,
		OffsetDateTime analyzedAt,
		int analysisSchemaVersion,
		String analyzerModelVersion,
		AnalysisResponse analysis) {

	static RepositoryAnalysisResponse from(RepositoryConnection connection, PersistedRepositoryAnalysis persisted) {
		RepositoryAnalysisSummary summary = persisted.summary();
		return new RepositoryAnalysisResponse(
				connection.id().toString(),
				connection.owner() + "/" + connection.name(),
				summary.workflowPath(),
				summary.analyzedAt(),
				summary.analysisSchemaVersion(),
				summary.analyzerModelVersion(),
				persisted.analysis());
	}
}
