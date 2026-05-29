package com.scaffy.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class DynamicClientRegistrationRepositoryTest {

	private final OAuthInstanceRepository instanceRepository = mock(OAuthInstanceRepository.class);

	private final ClientRegistration github = CommonOAuth2Provider.GITHUB.getBuilder("github")
			.clientId("gh-id")
			.clientSecret("gh-secret")
			.build();

	private final DynamicClientRegistrationRepository repository = new DynamicClientRegistrationRepository(
			Map.of("github", github), instanceRepository);

	@Test
	void returnsStaticRegistration() {
		assertThat(repository.findByRegistrationId("github")).isSameAs(github);
	}

	@Test
	void buildsGitlabRegistrationFromStoredInstance() {
		OAuthInstance instance = new OAuthInstance(
				UUID.randomUUID(),
				"gitlab-gitlab-example-com",
				"gitlab",
				"https://gitlab.example.com",
				"gitlab.example.com",
				"Company GitLab",
				"client-id",
				"client-secret");
		when(instanceRepository.findByRegistrationId("gitlab-gitlab-example-com")).thenReturn(Optional.of(instance));

		ClientRegistration registration = repository.findByRegistrationId("gitlab-gitlab-example-com");

		assertThat(registration).isNotNull();
		assertThat(registration.getClientId()).isEqualTo("client-id");
		assertThat(registration.getProviderDetails().getTokenUri())
				.isEqualTo("https://gitlab.example.com/oauth/token");
	}

	@Test
	void returnsNullForUnknownRegistration() {
		when(instanceRepository.findByRegistrationId("gitlab-missing")).thenReturn(Optional.empty());

		assertThat(repository.findByRegistrationId("gitlab-missing")).isNull();
		assertThat(repository.findByRegistrationId("unknown")).isNull();
	}
}
