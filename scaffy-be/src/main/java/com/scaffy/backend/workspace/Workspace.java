package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Workspace(
		UUID id,
		String name,
		String slug,
		OffsetDateTime createdAt) {
}
