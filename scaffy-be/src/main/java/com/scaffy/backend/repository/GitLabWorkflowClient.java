package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
	@SuppressWarnings("java:S5144") // Standard public GitLab REST path fragment, not a user-facing URI.
	private static final String PROJECTS_API_PATH = "/api/v4/projects/";
	private static final String JSON_CONTENT_TYPE = "application/json";
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

	public WorkflowCommitResult commitWorkflow(
			UUID workspaceId,
			UUID userId,
			RepositoryConnection repository,
			String workflowPath,
			String newContent,
			String commitMessage) {
		String host = repository.providerInstance() == null || repository.providerInstance().isBlank()
				? GITLAB_COM_HOST
				: repository.providerInstance();
		String baseUrl = gitLabRepositoryClient.resolveBaseUrl(host)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown GitLab instance: " + host));
		String accessToken = accessToken(workspaceId, userId, host);
		String projectId = encode(repository.owner() + "/" + repository.name());

		Map<String, Object> project = jsonObject(gitLabRequest(baseUrl, host, PROJECTS_API_PATH + projectId, accessToken));
		String defaultBranch = string(project.get("default_branch"));
		if (defaultBranch == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This GitLab project has no default branch yet.");
		}

		String requestBody = jsonString(Map.of(
				"branch", defaultBranch,
				"commit_message", commitMessage,
				"content", newContent,
				"encoding", "text"));

		String filePath = PROJECTS_API_PATH + projectId + "/repository/files/" + encode(workflowPath);
		gitLabSendJson("PUT", baseUrl, host, filePath, accessToken, requestBody);

		String commitSha = latestCommitSha(baseUrl, host, projectId, defaultBranch, workflowPath, accessToken);
		String commitUrl = commitSha == null
				? null
				: baseUrl + "/" + repository.owner() + "/" + repository.name() + "/-/commit/" + commitSha;
		log.info(
				"Committed GitLab workflow update userId={} host={} project={}/{} path={} branch={} sha={}",
				userId,
				host,
				repository.owner(),
				repository.name(),
				workflowPath,
				defaultBranch,
				commitSha);
		return new WorkflowCommitResult(commitSha, commitUrl, defaultBranch);
	}

	private String latestCommitSha(
			String baseUrl,
			String host,
			String projectId,
			String branch,
			String workflowPath,
			String accessToken) {
		String url = PROJECTS_API_PATH + projectId + "/repository/commits?ref_name=" + encode(branch)
				+ "&path=" + encode(workflowPath) + "&per_page=1";
		String body = gitLabRequest(baseUrl, host, url, accessToken);
		try {
			List<Map<String, Object>> commits = objectMapper.readValue(
					body,
					new TypeReference<List<Map<String, Object>>>() {
					});
			if (commits.isEmpty()) {
				return null;
			}
			return string(commits.get(0).get("id"));
		}
		catch (JacksonException ex) {
			log.warn("Could not parse latest commit response: {}", ex.getMessage());
			return null;
		}
	}

	private void gitLabSendJson(
			String method,
			String baseUrl,
			String host,
			String pathAndQuery,
			String accessToken,
			String requestBody) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
				.header("Accept", JSON_CONTENT_TYPE)
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", JSON_CONTENT_TYPE)
				.method(method, HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 || response.statusCode() == 403) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"GitLab authorization cannot write to this project. Reconnect GitLab and grant write access.");
			}
			if (response.statusCode() == 404) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GitLab project or workflow file was not found.");
			}
			if (response.statusCode() == 400 || response.statusCode() == 409) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"The workflow file has changed since the suggestion was generated. Reload the analysis and try again.");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("GitLab write failed status={} path={} body={}", response.statusCode(), pathAndQuery, response.body());
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab commit could not be created.");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab commit could not be created.", ex);
		}
		catch (IOException ex) {
			throw ProviderHttpErrors.unreachable("GitLab", host, ex);
		}
	}

	private String jsonString(Map<String, Object> value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode GitLab commit body.", ex);
		}
	}

	public GitLabCiFile findCiFile(UUID workspaceId, UUID userId, RepositoryConnection repository) {
		String host = repository.providerInstance() == null || repository.providerInstance().isBlank()
				? GITLAB_COM_HOST
				: repository.providerInstance();
		String baseUrl = gitLabRepositoryClient.resolveBaseUrl(host)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown GitLab instance: " + host));
		String accessToken = accessToken(workspaceId, userId, host);
		String projectId = encode(repository.owner() + "/" + repository.name());

		Map<String, Object> project = jsonObject(gitLabRequest(baseUrl, host, PROJECTS_API_PATH + projectId, accessToken));
		String defaultBranch = string(project.get("default_branch"));
		if (defaultBranch == null) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"This GitLab project has no default branch yet (empty repository).");
		}
		String ciPath = ciConfigPath(string(project.get("ci_config_path")));

		String rawPath = PROJECTS_API_PATH + projectId + "/repository/files/" + encode(ciPath)
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
				.header("Accept", JSON_CONTENT_TYPE)
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
			throw ProviderHttpErrors.unreachable("GitLab", host, ex);
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
