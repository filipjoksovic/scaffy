package com.scaffy.backend.auth;

import java.time.OffsetDateTime;

public record OAuthAccessTokenRecord(
		String encryptedAccessToken,
		OffsetDateTime expiresAt,
		String scopes) {
}
