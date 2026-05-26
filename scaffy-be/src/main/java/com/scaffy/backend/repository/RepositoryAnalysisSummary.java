package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryAnalysisSummary(
		UUID repositoryConnectionId,
		String workflowPath,
		String provider,
		double overallScore,
		int overallLevel,
		String overallStatus,
		OffsetDateTime analyzedAt,
		int analysisSchemaVersion,
		String analyzerModelVersion) {
}
