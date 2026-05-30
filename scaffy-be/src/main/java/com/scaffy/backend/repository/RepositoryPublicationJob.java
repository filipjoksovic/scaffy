package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryPublicationJob(
		UUID id,
		UUID userId,
		UUID workspaceId,
		UUID initJobId,
		String provider,
		String repositoryName,
		String repositoryDescription,
		String visibility,
		String status,
		String progressMessage,
		String errorMessage,
		String repositoryOwner,
		String repositoryUrl,
		UUID repositoryConnectionId,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {
}
