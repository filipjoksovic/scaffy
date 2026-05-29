package com.scaffy.backend.auth;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public final class GitLabClientRegistrations {

	static final String[] SCOPES = { "read_user", "read_api", "read_repository" };

	private GitLabClientRegistrations() {
	}

	public static ClientRegistration build(String registrationId, String baseUrl, String clientId, String clientSecret) {
		String base = trimTrailingSlash(baseUrl);
		return ClientRegistration.withRegistrationId(registrationId)
				.clientId(clientId)
				.clientSecret(clientSecret)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope(SCOPES)
				.authorizationUri(base + "/oauth/authorize")
				.tokenUri(base + "/oauth/token")
				.userInfoUri(base + "/api/v4/user")
				.userNameAttributeName("id")
				.clientName(registrationId)
				.build();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}
}
