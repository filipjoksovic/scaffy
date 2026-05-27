package com.scaffy.backend.repository;

import java.time.OffsetDateTime;

public record RepositoryPublicationJobLogLine(
		long id,
		String stream,
		String message,
		OffsetDateTime createdAt) {
}
