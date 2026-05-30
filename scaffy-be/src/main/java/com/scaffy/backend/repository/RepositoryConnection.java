package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryConnection(
		UUID id,
		UUID workspaceId,
		UUID userId,
		String provider,
		String providerInstance,
		String owner,
		String name,
		String url,
		OffsetDateTime connectedAt) {
}
