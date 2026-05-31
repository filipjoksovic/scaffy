package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import com.scaffy.backend.auth.OAuthInstanceRepository;
import com.scaffy.backend.auth.ProviderTokenCrypto;
import com.scaffy.backend.auth.WorkspaceOAuthTokenRepository;

@Service
public class GitLabRepositoryClient {

	private static final Logger log = LoggerFactory.getLogger(GitLabRepositoryClient.class);

	private static final String GITLAB_COM_HOST = "gitlab.com";
	private static final String GITLAB_COM_BASE_URL = "https://gitlab.com";

	private static final TypeReference<List<Map<String, Object>>> PROJECTS_TYPE = new TypeReference<>() {
	};

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final WorkspaceOAuthTokenRepository tokenRepository;
	private final OAuthInstanceRepository instanceRepository;

	@Autowired
	public GitLabRepositoryClient(
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository,
			OAuthInstanceRepository instanceRepository) {
		this(HttpClient.newHttpClient(), objectMapper, providerTokenCrypto, tokenRepository, instanceRepository);
	}

	GitLabRepositoryClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository,
			OAuthInstanceRepository instanceRepository) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.providerTokenCrypto = providerTokenCrypto;
		this.tokenRepository = tokenRepository;
		this.instanceRepository = instanceRepository;
	}

	/** Resolves the canonical base URL for a GitLab host, or empty if it is unknown to this server. */
	public Optional<String> resolveBaseUrl(String host) {
		if (host == null || host.isBlank() || GITLAB_COM_HOST.equalsIgnoreCase(host)) {
			return Optional.of(GITLAB_COM_BASE_URL);
		}
		return instanceRepository.findBaseUrlByHost(host.toLowerCase());
	}

	public List<GitLabProjectOption> findProjects(UUID workspaceId, UUID userId, String host) {
		String normalizedHost = host == null || host.isBlank() ? GITLAB_COM_HOST : host.toLowerCase();
		String baseUrl = resolveBaseUrl(normalizedHost)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Unknown GitLab instance: " + normalizedHost));

		Optional<OAuthAccessTokenRecord> tokenRecord =
				tokenRepository.findToken(workspaceId, userId, "gitlab", normalizedHost);
		if (tokenRecord.isEmpty()) {
			log.warn("No GitLab token for workspaceId={} userId={} host={}", workspaceId, userId, normalizedHost);
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Connect this GitLab account in this workspace before fetching projects.");
		}
		String accessToken = providerTokenCrypto.decrypt(tokenRecord.get().encryptedAccessToken());

		List<GitLabProjectOption> projects = new ArrayList<>();
		for (int page = 1; page <= 5; page++) {
			List<GitLabProjectOption> pageItems = fetchProjectPage(baseUrl, normalizedHost, accessToken, page);
			projects.addAll(pageItems);
			if (pageItems.size() < 100) {
				break;
			}
		}
		log.info("Fetched {} GitLab projects for userId={} host={}", projects.size(), userId, normalizedHost);
		return projects;
	}

	private List<GitLabProjectOption> fetchProjectPage(String baseUrl, String host, String accessToken, int page) {
		String url = baseUrl + "/api/v4/projects?membership=true&simple=true&per_page=100"
				+ "&order_by=last_activity_at&page=" + page;
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 || response.statusCode() == 403) {
				log.warn("GitLab project fetch authorization failed with status={}", response.statusCode());
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"GitLab authorization expired. Reconnect this GitLab account.");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("GitLab project fetch failed with status={}", response.statusCode());
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab projects could not be loaded.");
			}
			return objectMapper.readValue(response.body(), PROJECTS_TYPE)
					.stream()
					.map(this::mapProject)
					.toList();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab projects could not be loaded.", ex);
		}
		catch (IOException ex) {
			throw ProviderHttpErrors.unreachable("GitLab", host, ex);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab projects could not be parsed.", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private GitLabProjectOption mapProject(Map<String, Object> project) {
		String pathWithNamespace = string(project.get("path_with_namespace"));
		String name = string(project.get("name"));
		String owner = pathWithNamespace;
		if (pathWithNamespace != null && pathWithNamespace.contains("/")) {
			owner = pathWithNamespace.substring(0, pathWithNamespace.lastIndexOf('/'));
		}
		Map<String, Object> namespace = (Map<String, Object>) project.get("namespace");
		if (namespace != null && namespace.get("full_path") != null) {
			owner = string(namespace.get("full_path"));
		}
		String visibility = string(project.get("visibility"));
		return new GitLabProjectOption(
				pathWithNamespace,
				owner,
				name,
				string(project.get("web_url")),
				!"public".equalsIgnoreCase(visibility));
	}

	private String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
