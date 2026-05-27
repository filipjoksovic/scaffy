package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RepositoryPublicationResponse(
		UUID publicationJobId,
		String status,
		String progress,
		String errorMessage,
		String provider,
		String repositoryName,
		String visibility,
		String repositoryOwner,
		String repositoryUrl,
		RepositoryConnectionController.RepositoryConnectionResponse repositoryConnection,
		List<RepositoryPublicationJobLogLine> logs,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {
}
