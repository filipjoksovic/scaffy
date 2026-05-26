package com.scaffy.backend.auth;

public record OAuthProfile(
		String provider,
		String providerUserId,
		String email,
		String displayName,
		String avatarUrl) {
}
