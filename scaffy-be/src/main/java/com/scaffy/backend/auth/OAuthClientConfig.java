package com.scaffy.backend.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OAuthClientConfig {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(OAuthClientProperties properties) {
		Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
		if (properties.google() != null && properties.google().configured()) {
			registrations.put("google", CommonOAuth2Provider.GOOGLE.getBuilder("google")
					.clientId(properties.google().clientId())
					.clientSecret(properties.google().clientSecret())
					.scope("openid", "profile", "email")
					.build());
		}
		if (properties.github() != null && properties.github().configured()) {
			registrations.put("github", CommonOAuth2Provider.GITHUB.getBuilder("github")
					.clientId(properties.github().clientId())
					.clientSecret(properties.github().clientSecret())
					.scope("repo", "workflow", "read:user", "user:email")
					.build());
		}
		return registrations::get;
	}
}
