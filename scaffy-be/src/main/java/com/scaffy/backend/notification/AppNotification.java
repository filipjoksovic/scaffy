package com.scaffy.backend.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppNotification(
		UUID id,
		UUID userId,
		UUID workspaceId,
		String type,
		String title,
		String message,
		String targetUrl,
		OffsetDateTime readAt,
		OffsetDateTime createdAt) {
}
