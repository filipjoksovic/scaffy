package com.scaffy.backend.auth;

public record OAuthInstanceSummary(
		String registrationId,
		String host,
		String displayName) {

	static OAuthInstanceSummary from(OAuthInstance instance) {
		return new OAuthInstanceSummary(
				instance.registrationId(),
				instance.host(),
				instance.displayName());
	}
}
