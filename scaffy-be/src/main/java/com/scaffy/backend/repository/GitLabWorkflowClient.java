package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import com.scaffy.backend.auth.WorkspaceOAuthTokenRepository;

@Service
public class GitLabWorkflowClient {

	private static final Logger log = LoggerFactory.getLogger(GitLabWorkflowClient.class);
	private static final String GITLAB_COM_HOST = "gitlab.com";
	private static final String DEFAULT_CI_PATH = ".gitlab-ci.yml";
	private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {
	};

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final WorkspaceOAuthTokenRepository tokenRepository;
	private final GitLabRepositoryClient gitLabRepositoryClient;

	@Autowired
	public GitLabWorkflowClient(
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository,
			GitLabRepositoryClient gitLabRepositoryClient) {
		this(HttpClient.newHttpClient(), objectMapper, providerTokenCrypto, tokenRepository, gitLabRepositoryClient);
	}

	GitLabWorkflowClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository,
			GitLabRepositoryClient gitLabRepositoryClient) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.providerTokenCrypto = providerTokenCrypto;
		this.tokenRepository = tokenRepository;
		this.gitLabRepositoryClient = gitLabRepositoryClient;
	}

	public GitLabCiFile findCiFile(UUID workspaceId, UUID userId, RepositoryConnection repository) {
		String host = repository.providerInstance() == null || repository.providerInstance().isBlank()
				? GITLAB_COM_HOST
				: repository.providerInstance();
		String baseUrl = gitLabRepositoryClient.resolveBaseUrl(host)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown GitLab instance: " + host));
		String accessToken = accessToken(workspaceId, userId, host);
		String projectId = encode(repository.owner() + "/" + repository.name());

		Map<String, Object> project = jsonObject(gitLabRequest(baseUrl, host, "/api/v4/projects/" + projectId, accessToken));
		String defaultBranch = string(project.get("default_branch"));
		if (defaultBranch == null) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"This GitLab project has no default branch yet (empty repository).");
		}
		String ciPath = ciConfigPath(string(project.get("ci_config_path")));

		String rawPath = "/api/v4/projects/" + projectId + "/repository/files/" + encode(ciPath)
				+ "/raw?ref=" + encode(defaultBranch);
		String content = gitLabRequest(baseUrl, host, rawPath, accessToken);
		log.info(
				"Fetched GitLab CI file userId={} host={} project={}/{} path={}",
				userId,
				host,
				repository.owner(),
				repository.name(),
				ciPath);
		return new GitLabCiFile(ciPath, content);
	}

	private String ciConfigPath(String configured) {
		if (configured == null || configured.isBlank()) {
			return DEFAULT_CI_PATH;
		}
		// A ci_config_path may point at an external project ("path@group/project"); the external part
		// is not fetchable from this project, so fall back to the local path component.
		int at = configured.indexOf('@');
		String local = at >= 0 ? configured.substring(0, at) : configured;
		local = local.trim();
		return local.isBlank() ? DEFAULT_CI_PATH : local;
	}

	private String accessToken(UUID workspaceId, UUID userId, String host) {
		OAuthAccessTokenRecord token = tokenRepository.findToken(workspaceId, userId, "gitlab", host)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.CONFLICT,
						"Connect this GitLab account in this workspace before analyzing the project."));
		return providerTokenCrypto.decrypt(token.encryptedAccessToken());
	}

	private String gitLabRequest(String baseUrl, String host, String pathAndQuery, String accessToken) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 || response.statusCode() == 403) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"GitLab authorization cannot read this project. Reconnect GitLab and grant repository access.");
			}
			if (response.statusCode() == 404) {
				throw new ResponseStatusException(
						HttpStatus.UNPROCESSABLE_ENTITY,
						"No .gitlab-ci.yml file was found in this GitLab project.");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("GitLab request failed status={} path={}", response.statusCode(), pathAndQuery);
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab project files could not be loaded.");
			}
			return response.body();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab project files could not be loaded.", ex);
		}
		catch (IOException ex) {
			throw ProviderHttpException.unreachable("GitLab", host, ex);
		}
	}

	private Map<String, Object> jsonObject(String content) {
		try {
			return objectMapper.readValue(content, OBJECT_TYPE);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab response could not be parsed.", ex);
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private String string(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? null : text;
	}
}
