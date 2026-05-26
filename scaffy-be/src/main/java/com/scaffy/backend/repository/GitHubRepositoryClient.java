package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.scaffy.backend.auth.OAuthAccessTokenRecord;
import com.scaffy.backend.auth.ProviderTokenCrypto;
import com.scaffy.backend.auth.UserRepository;

@Service
public class GitHubRepositoryClient {

	private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryClient.class);

	private static final TypeReference<List<Map<String, Object>>> REPOSITORIES_TYPE = new TypeReference<>() {
	};

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final UserRepository userRepository;

	@Autowired
	public GitHubRepositoryClient(
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			UserRepository userRepository) {
		this(HttpClient.newHttpClient(), objectMapper, providerTokenCrypto, userRepository);
	}

	GitHubRepositoryClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			UserRepository userRepository) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.providerTokenCrypto = providerTokenCrypto;
		this.userRepository = userRepository;
	}

	public List<GitHubRepositoryOption> findRepositories(UUID userId) {
		log.info("Fetching GitHub repositories for userId={}", userId);
		Optional<OAuthAccessTokenRecord> tokenRecord = userRepository.findOAuthAccessToken(userId, "github");
		if (tokenRecord.isEmpty()) {
			log.warn("No stored GitHub OAuth access token found for userId={}", userId);
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Reconnect with GitHub before fetching repositories.");
		}
		OAuthAccessTokenRecord token = tokenRecord.get();
		if (token.expiresAt() != null) {
			log.info(
					"Stored GitHub OAuth access token has provider expiry metadata for userId={} expiresAt={}; deferring validity check to GitHub API",
					userId,
					token.expiresAt());
		}

		String accessToken = providerTokenCrypto.decrypt(token.encryptedAccessToken());
		List<GitHubRepositoryOption> repositories = new ArrayList<>();
		for (int page = 1; page <= 5; page++) {
			List<GitHubRepositoryOption> pageItems = fetchRepositoryPage(accessToken, page);
			repositories.addAll(pageItems);
			if (pageItems.size() < 100) {
				break;
			}
		}
		log.info("Fetched {} GitHub repositories for userId={}", repositories.size(), userId);
		return repositories;
	}

	private List<GitHubRepositoryOption> fetchRepositoryPage(String accessToken, int page) {
		String query = "per_page=100&page=" + page + "&sort=updated&affiliation=" + encode("owner,collaborator,organization_member");
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/user/repos?" + query))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 || response.statusCode() == 403) {
				log.warn("GitHub repository fetch authorization failed with status={}", response.statusCode());
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"GitHub authorization expired. Reconnect with GitHub before fetching repositories.");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("GitHub repository fetch failed with status={}", response.statusCode());
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repositories could not be loaded.");
			}
			return objectMapper.readValue(response.body(), REPOSITORIES_TYPE)
					.stream()
					.map(this::mapRepository)
					.toList();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repositories could not be loaded.", ex);
		}
		catch (IOException | JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repositories could not be loaded.", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private GitHubRepositoryOption mapRepository(Map<String, Object> repository) {
		Map<String, Object> owner = (Map<String, Object>) repository.get("owner");
		String ownerLogin = string(owner == null ? null : owner.get("login"));
		String name = string(repository.get("name"));
		return new GitHubRepositoryOption(
				string(repository.get("full_name")),
				ownerLogin,
				name,
				string(repository.get("html_url")),
				Boolean.TRUE.equals(repository.get("private")));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
