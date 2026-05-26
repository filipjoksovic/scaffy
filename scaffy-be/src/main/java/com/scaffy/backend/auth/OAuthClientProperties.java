package com.scaffy.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.oauth")
public record OAuthClientProperties(Provider google, Provider github) {

	public record Provider(String clientId, String clientSecret) {

		boolean configured() {
			return hasText(clientId) && hasText(clientSecret);
		}

		private boolean hasText(String value) {
			return value != null && !value.isBlank();
		}
	}
}
