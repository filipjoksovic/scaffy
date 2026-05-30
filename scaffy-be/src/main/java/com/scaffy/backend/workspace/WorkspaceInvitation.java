package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceInvitation(
		UUID id,
		UUID workspaceId,
		String workspaceName,
		String email,
		String role,
		String token,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime expiresAt) {
}
