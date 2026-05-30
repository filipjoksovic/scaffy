package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryAnalysisSummary(
		UUID id,
		UUID repositoryConnectionId,
		int runNumber,
		String workflowPath,
		String workflowContentHash,
		String provider,
		double overallScore,
		int overallLevel,
		String overallStatus,
		OffsetDateTime analyzedAt,
		int analysisSchemaVersion,
		String analyzerModelVersion,
		String status,
		String errorMessage) {

	public boolean failed() {
		return "failed".equals(status);
	}
}
