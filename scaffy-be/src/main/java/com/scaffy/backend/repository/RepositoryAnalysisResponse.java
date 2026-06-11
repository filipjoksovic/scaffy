package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

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
		AnalysisResponse analysis,
		@JsonInclude(JsonInclude.Include.NON_NULL) WorkflowMetricsResult workflowMetrics,
		List<WorkflowAnalysisItem> workflowAnalyses) {

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
				persisted.analysis(),
				persisted.workflowMetrics(),
				persisted.workflowAnalyses());
	}
}

