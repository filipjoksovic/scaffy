package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryAnalysisJob(
		UUID id,
		UUID workspaceId,
		UUID userId,
		UUID repositoryConnectionId,
		UUID analysisRunId,
		String status,
		String progressMessage,
		int progressPercent,
		String errorMessage,
		int attemptCount,
		int maxAttempts,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {
}
