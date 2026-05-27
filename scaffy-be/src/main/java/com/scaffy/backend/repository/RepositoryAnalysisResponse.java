package com.scaffy.backend.repository;

import java.time.OffsetDateTime;

import com.scaffy.backend.analyze.AnalysisResponse;

public record RepositoryAnalysisResponse(
		String runId,
		String repositoryId,
		String repository,
		int runNumber,
		String workflowPath,
		String workflowContentHash,
		String workflowContent,
		OffsetDateTime analyzedAt,
		int analysisSchemaVersion,
		String analyzerModelVersion,
		AnalysisResponse analysis) {

	static RepositoryAnalysisResponse from(RepositoryConnection connection, PersistedRepositoryAnalysis persisted) {
		RepositoryAnalysisSummary summary = persisted.summary();
		return new RepositoryAnalysisResponse(
				summary.id().toString(),
				connection.id().toString(),
				connection.owner() + "/" + connection.name(),
				summary.runNumber(),
				summary.workflowPath(),
				summary.workflowContentHash(),
				persisted.workflowContent(),
				summary.analyzedAt(),
				summary.analysisSchemaVersion(),
				summary.analyzerModelVersion(),
				persisted.analysis());
	}
}
