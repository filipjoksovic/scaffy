package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceMember(
		UUID userId,
		String email,
		String displayName,
		String avatarUrl,
		String role,
		OffsetDateTime joinedAt) {
}
