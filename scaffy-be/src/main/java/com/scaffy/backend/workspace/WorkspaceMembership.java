package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceMembership(
		UUID workspaceId,
		String name,
		String slug,
		String role,
		OffsetDateTime joinedAt) {
}
