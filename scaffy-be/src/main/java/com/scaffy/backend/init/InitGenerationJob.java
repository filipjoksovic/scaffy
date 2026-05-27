package com.scaffy.backend.init;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InitGenerationJob(
		UUID id,
		String status,
		String projectName,
		String requestJson,
		String selectionJson,
		String progressMessage,
		String errorMessage,
		String artifactObjectKey,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {
}
