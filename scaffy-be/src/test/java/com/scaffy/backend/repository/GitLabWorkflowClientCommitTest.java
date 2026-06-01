package com.scaffy.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import com.scaffy.backend.auth.OAuthAccessTokenRecord;
import com.scaffy.backend.auth.ProviderTokenCrypto;
import com.scaffy.backend.auth.WorkspaceOAuthTokenRepository;

class GitLabWorkflowClientCommitTest {

	private HttpClient httpClient;
	private ProviderTokenCrypto crypto;
	private WorkspaceOAuthTokenRepository tokenRepository;
	private GitLabRepositoryClient gitLabRepositoryClient;
	private GitLabWorkflowClient client;

	private final UUID workspaceId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		httpClient = mock(HttpClient.class);
		crypto = mock(ProviderTokenCrypto.class);
		tokenRepository = mock(WorkspaceOAuthTokenRepository.class);
		gitLabRepositoryClient = mock(GitLabRepositoryClient.class);
		client = new GitLabWorkflowClient(
				httpClient, new ObjectMapper(), crypto, tokenRepository, gitLabRepositoryClient);

		when(gitLabRepositoryClient.resolveBaseUrl("gitlab.com"))
				.thenReturn(Optional.of("https://gitlab.com"));
		OAuthAccessTokenRecord token = new OAuthAccessTokenRecord(
				"encrypted-token",
				OffsetDateTime.now().plusHours(1),
				"api");
		when(tokenRepository.findToken(workspaceId, userId, "gitlab", "gitlab.com")).thenReturn(Optional.of(token));
		when(crypto.decrypt("encrypted-token")).thenReturn("glab-token");
	}

	@Test
	void commitWorkflowSendsContentAndReturnsLatestCommitMetadata() throws Exception {
		HttpResponse<String> projectMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> putResponse = okResponse("""
				{ "file_path": ".gitlab-ci.yml", "branch": "main" }
				""");
		HttpResponse<String> commitsResponse = okResponse("""
				[ { "id": "def456" } ]
				""");

		doReturn(projectMeta, putResponse, commitsResponse).when(httpClient).send(any(HttpRequest.class), any());

		WorkflowCommitResult result = client.commitWorkflow(
				workspaceId,
				userId,
				connection(),
				".gitlab-ci.yml",
				"new yaml",
				"Improve CI/CD pipeline quality");

		assertThat(result.branch()).isEqualTo("main");
		assertThat(result.commitSha()).isEqualTo("def456");
		assertThat(result.commitUrl()).contains("/o/r/-/commit/def456");
	}

	@Test
	void commitWorkflowReturnsNullShaWhenCommitsResponseIsEmpty() throws Exception {
		HttpResponse<String> projectMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> putResponse = okResponse("{}");
		HttpResponse<String> commitsResponse = okResponse("[]");

		doReturn(projectMeta, putResponse, commitsResponse).when(httpClient).send(any(HttpRequest.class), any());

		WorkflowCommitResult result = client.commitWorkflow(
				workspaceId, userId, connection(), ".gitlab-ci.yml", "yaml", "msg");

		assertThat(result.branch()).isEqualTo("main");
		assertThat(result.commitSha()).isNull();
		assertThat(result.commitUrl()).isNull();
	}

	@Test
	void commitWorkflowMapsAuthFailuresToConflict() throws Exception {
		HttpResponse<String> projectMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> forbidden = stringResponse(403, "");
		doReturn(projectMeta, forbidden).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".gitlab-ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("Reconnect");
	}

	@Test
	void commitWorkflowMaps409ToConflict() throws Exception {
		HttpResponse<String> projectMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> conflict = stringResponse(409, "{}");
		doReturn(projectMeta, conflict).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".gitlab-ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("changed since");
	}

	@Test
	void commitWorkflowFailsWhenProjectHasNoDefaultBranch() throws Exception {
		HttpResponse<String> projectMeta = okResponse("{}");
		doReturn(projectMeta).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".gitlab-ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("default branch");
	}

	private RepositoryConnection connection() {
		return new RepositoryConnection(
				UUID.randomUUID(),
				workspaceId,
				userId,
				"gitlab",
				"gitlab.com",
				"o",
				"r",
				"https://gitlab.com/o/r",
				OffsetDateTime.now());
	}

	private HttpResponse<String> okResponse(String body) {
		return stringResponse(200, body);
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> stringResponse(int status, String body) {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		return response;
	}
}
