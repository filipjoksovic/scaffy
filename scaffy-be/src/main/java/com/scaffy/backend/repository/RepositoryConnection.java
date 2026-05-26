package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryConnection(
		UUID id,
		UUID userId,
		String provider,
		String owner,
		String name,
		String url,
		OffsetDateTime connectedAt) {
}
