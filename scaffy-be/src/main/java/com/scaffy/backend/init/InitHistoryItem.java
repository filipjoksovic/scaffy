package com.scaffy.backend.init;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InitHistoryItem(
		UUID jobId,
		String projectName,
		StackSummary stack,
		String status,
		OffsetDateTime createdAt) {

	public record StackSummary(
			String frontend,
			String backend,
			String pipeline) {
	}
}
