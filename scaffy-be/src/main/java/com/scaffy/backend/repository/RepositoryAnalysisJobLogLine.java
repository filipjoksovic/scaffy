package com.scaffy.backend.repository;

import java.time.OffsetDateTime;

public record RepositoryAnalysisJobLogLine(
		long id,
		String stream,
		String message,
		OffsetDateTime createdAt) {
}
