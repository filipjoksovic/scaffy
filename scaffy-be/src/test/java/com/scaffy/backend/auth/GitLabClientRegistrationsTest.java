package com.scaffy.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class GitLabClientRegistrationsTest {

	@Test
	void buildsEndpointsFromBaseUrlAndTrimsTrailingSlash() {
		ClientRegistration registration = GitLabClientRegistrations.build(
				"gitlab-gitlab-example-com",
				"https://gitlab.example.com/",
				"client-id",
				"client-secret");

		assertThat(registration.getRegistrationId()).isEqualTo("gitlab-gitlab-example-com");
		assertThat(registration.getClientId()).isEqualTo("client-id");
		assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
		assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
		assertThat(registration.getScopes()).containsExactlyInAnyOrder("read_user", "read_api", "read_repository");

		ClientRegistration.ProviderDetails details = registration.getProviderDetails();
		assertThat(details.getAuthorizationUri()).isEqualTo("https://gitlab.example.com/oauth/authorize");
		assertThat(details.getTokenUri()).isEqualTo("https://gitlab.example.com/oauth/token");
		assertThat(details.getUserInfoEndpoint().getUri()).isEqualTo("https://gitlab.example.com/api/v4/user");
		assertThat(details.getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
	}
}
