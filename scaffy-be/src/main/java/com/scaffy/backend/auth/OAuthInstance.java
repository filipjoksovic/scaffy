package com.scaffy.backend.auth;

import java.util.UUID;

public record OAuthInstance(
		UUID id,
		String registrationId,
		String provider,
		String baseUrl,
		String host,
		String displayName,
		String clientId,
		String clientSecret) {

	public static String registrationIdForHost(String host) {
		return "gitlab-" + slug(host);
	}

	static String slug(String host) {
		String normalized = host == null ? "" : host.trim().toLowerCase();
		String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
		return slug.isBlank() ? "instance" : slug;
	}
}
