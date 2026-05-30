package com.scaffy.backend.auth;

import java.time.OffsetDateTime;

public record ProviderConnectionRecord(
		String provider,
		String instance,
		String displayName,
		String scopes,
		boolean hasToken,
		OffsetDateTime updatedAt) {
}
