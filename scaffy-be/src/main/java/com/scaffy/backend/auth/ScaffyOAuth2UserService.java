package com.scaffy.backend.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ScaffyOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private static final TypeReference<List<Map<String, Object>>> GITHUB_EMAILS_TYPE = new TypeReference<>() {
	};

	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Autowired
	public ScaffyOAuth2UserService(ObjectMapper objectMapper) {
		this(objectMapper, HttpClient.newHttpClient());
	}

	ScaffyOAuth2UserService(ObjectMapper objectMapper, HttpClient httpClient) {
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User user = delegate.loadUser(userRequest);
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		if (!"github".equals(registrationId) || !isBlank(user.getAttribute("email"))) {
			return user;
		}

		String email = fetchGitHubEmail(userRequest.getAccessToken().getTokenValue());
		if (isBlank(email)) {
			return user;
		}

		Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
		attributes.put("email", email);
		String userNameAttributeName = userRequest.getClientRegistration()
				.getProviderDetails()
				.getUserInfoEndpoint()
				.getUserNameAttributeName();
		return new DefaultOAuth2User(user.getAuthorities(), attributes, userNameAttributeName);
	}

	private String fetchGitHubEmail(String accessToken) {
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/user/emails"))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			List<Map<String, Object>> emails = objectMapper.readValue(response.body(), GITHUB_EMAILS_TYPE);
			return emails.stream()
					.filter(email -> Boolean.TRUE.equals(email.get("primary")))
					.filter(email -> Boolean.TRUE.equals(email.get("verified")))
					.map(email -> email.get("email"))
					.filter(String.class::isInstance)
					.map(String.class::cast)
					.findFirst()
					.orElse(null);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (IOException | JacksonException ex) {
			return null;
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
