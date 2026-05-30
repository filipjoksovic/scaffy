package com.scaffy.backend.repository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
public class GitHubWorkflowClient {

	private static final Logger log = LoggerFactory.getLogger(GitHubWorkflowClient.class);
	private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {
	};

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final WorkspaceOAuthTokenRepository tokenRepository;

	@Autowired
	public GitHubWorkflowClient(
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository) {
		this(HttpClient.newHttpClient(), objectMapper, providerTokenCrypto, tokenRepository);
	}

	GitHubWorkflowClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			ProviderTokenCrypto providerTokenCrypto,
			WorkspaceOAuthTokenRepository tokenRepository) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.providerTokenCrypto = providerTokenCrypto;
		this.tokenRepository = tokenRepository;
	}

	public GitHubWorkflowFile findWorkflow(UUID workspaceId, UUID userId, RepositoryConnection repository) {
		String accessToken = accessToken(workspaceId, userId);
		String defaultBranch = defaultBranch(repository, accessToken);
		List<String> workflowPaths = workflowPaths(repository, defaultBranch, accessToken);
		if (workflowPaths.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"No GitHub Actions workflow files were found under .github/workflows.");
		}
		String selectedPath = workflowPaths.stream()
				.min(Comparator.comparingInt(this::workflowPriority).thenComparing(path -> path))
				.orElseThrow();
		log.info(
				"Selected GitHub Actions workflow userId={} repository={}/{} path={}",
				userId,
				repository.owner(),
				repository.name(),
				selectedPath);
		return new GitHubWorkflowFile(selectedPath, rawContent(repository, defaultBranch, selectedPath, accessToken));
	}

	private String accessToken(UUID workspaceId, UUID userId) {
		OAuthAccessTokenRecord token = tokenRepository.findToken(workspaceId, userId, "github", "")
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.CONFLICT,
						"Connect GitHub in this workspace before analyzing repositories."));
		return providerTokenCrypto.decrypt(token.encryptedAccessToken());
	}

	private String defaultBranch(RepositoryConnection repository, String accessToken) {
		Map<String, Object> body = jsonObject(gitHubRequest(
				"/repos/" + encode(repository.owner()) + "/" + encode(repository.name()),
				accessToken,
				"application/vnd.github+json"));
		String defaultBranch = string(body.get("default_branch"));
		if (defaultBranch == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository metadata could not be read.");
		}
		return defaultBranch;
	}

	@SuppressWarnings("unchecked")
	private List<String> workflowPaths(RepositoryConnection repository, String defaultBranch, String accessToken) {
		Map<String, Object> body = jsonObject(gitHubRequest(
				"/repos/" + encode(repository.owner()) + "/" + encode(repository.name()) + "/git/trees/" + encode(defaultBranch)
						+ "?recursive=1",
				accessToken,
				"application/vnd.github+json"));
		Object treeValue = body.get("tree");
		if (!(treeValue instanceof List<?> tree)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository tree could not be read.");
		}
		if (Boolean.TRUE.equals(body.get("truncated"))) {
			log.warn("GitHub tree response was truncated for repository={}/{}", repository.owner(), repository.name());
		}
		return tree.stream()
				.filter(Map.class::isInstance)
				.map(item -> (Map<String, Object>) item)
				.filter(item -> "blob".equals(item.get("type")))
				.map(item -> string(item.get("path")))
				.filter(this::isWorkflowPath)
				.toList();
	}

	private String rawContent(
			RepositoryConnection repository,
			String defaultBranch,
			String path,
			String accessToken) {
		return gitHubRequest(
				"/repos/" + encode(repository.owner()) + "/" + encode(repository.name()) + "/contents/" + encodePath(path)
						+ "?ref=" + encode(defaultBranch),
				accessToken,
				"application/vnd.github.raw");
	}

	private String gitHubRequest(String pathAndQuery, String accessToken, String accept) {
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com" + pathAndQuery))
				.header("Accept", accept)
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401 || response.statusCode() == 403) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"GitHub authorization cannot read this repository. Reconnect with GitHub and grant repository access.");
			}
			if (response.statusCode() == 404) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub repository or workflow file was not found.");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("GitHub request failed status={} path={}", response.statusCode(), pathAndQuery);
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository files could not be loaded.");
			}
			return response.body();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository files could not be loaded.", ex);
		}
		catch (IOException ex) {
			throw ProviderHttpException.unreachable("GitHub", "api.github.com", ex);
		}
	}

	private Map<String, Object> jsonObject(String content) {
		try {
			return objectMapper.readValue(content, OBJECT_TYPE);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub response could not be parsed.", ex);
		}
	}

	private boolean isWorkflowPath(String path) {
		if (path == null) {
			return false;
		}
		String normalized = path.toLowerCase(Locale.ROOT);
		return normalized.startsWith(".github/workflows/")
				&& (normalized.endsWith(".yml") || normalized.endsWith(".yaml"));
	}

	private int workflowPriority(String path) {
		String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		if (fileName.equals("ci.yml") || fileName.equals("ci.yaml")) {
			return 0;
		}
		if (fileName.contains("ci") || fileName.contains("build") || fileName.contains("test")) {
			return 1;
		}
		if (fileName.equals("main.yml") || fileName.equals("main.yaml")) {
			return 2;
		}
		return 3;
	}

	private String encodePath(String path) {
		return java.util.Arrays.stream(path.split("/"))
				.map(this::encode)
				.reduce((left, right) -> left + "/" + right)
				.orElse("");
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
