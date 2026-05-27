package com.scaffy.backend.init;

import java.time.OffsetDateTime;

public record InitJobLogLine(
		long id,
		String stream,
		String message,
		OffsetDateTime createdAt) {
}
