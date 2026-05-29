package com.scaffy.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuthProfileExtractorTest {

	private final OAuthProfileExtractor extractor = new OAuthProfileExtractor();

	@Test
	void mapsGitlabComProfile() {
		OAuth2User user = gitlabUser(Map.of(
				"id", 12345,
				"email", "dev@example.com",
				"name", "Dev User",
				"username", "devuser",
				"avatar_url", "https://gitlab.com/avatar.png"));

		OAuthProfile profile = extractor.extract("gitlab", "gitlab.com", user);

		assertThat(profile.provider()).isEqualTo("gitlab");
		assertThat(profile.providerUserId()).isEqualTo("12345");
		assertThat(profile.email()).isEqualTo("dev@example.com");
		assertThat(profile.displayName()).isEqualTo("Dev User");
		assertThat(profile.avatarUrl()).isEqualTo("https://gitlab.com/avatar.png");
		assertThat(profile.instance()).isEqualTo("gitlab.com");
	}

	@Test
	void mapsSelfHostedInstanceAndFallsBackToUsername() {
		OAuth2User user = gitlabUser(Map.of(
				"id", 7,
				"username", "selfhosted",
				"avatar_url", "https://gitlab.example.com/avatar.png"));

		OAuthProfile profile = extractor.extract("gitlab-gitlab-example-com", "gitlab.example.com", user);

		assertThat(profile.provider()).isEqualTo("gitlab");
		assertThat(profile.providerUserId()).isEqualTo("7");
		assertThat(profile.displayName()).isEqualTo("selfhosted");
		assertThat(profile.instance()).isEqualTo("gitlab.example.com");
	}

	private OAuth2User gitlabUser(Map<String, Object> attributes) {
		return new DefaultOAuth2User(
				List.of(new SimpleGrantedAuthority("ROLE_USER")),
				attributes,
				"id");
	}
}
