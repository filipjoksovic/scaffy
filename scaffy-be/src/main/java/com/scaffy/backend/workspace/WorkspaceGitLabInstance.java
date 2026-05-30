package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceGitLabInstance(
		UUID id,
		UUID workspaceId,
		String registrationId,
		String host,
		String baseUrl,
		String displayName,
		UUID createdByUserId,
		OffsetDateTime createdAt) {
}
