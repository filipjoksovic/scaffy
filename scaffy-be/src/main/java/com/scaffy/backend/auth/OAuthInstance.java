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
		String collapsed = normalized.replaceAll("[^a-z0-9]+", "-");
		int start = 0;
		int end = collapsed.length();
		while (start < end && collapsed.charAt(start) == '-') {
			start++;
		}
		while (end > start && collapsed.charAt(end - 1) == '-') {
			end--;
		}
		String slug = collapsed.substring(start, end);
		return slug.isBlank() ? "instance" : slug;
	}
}
