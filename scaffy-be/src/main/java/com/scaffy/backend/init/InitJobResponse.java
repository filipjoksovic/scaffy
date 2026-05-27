package com.scaffy.backend.init;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InitJobResponse(
		UUID jobId,
		String status,
		String progress,
		String errorMessage,
		InitSelection selection,
		boolean downloadAvailable,
		List<InitJobLogLine> logs,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {
}
