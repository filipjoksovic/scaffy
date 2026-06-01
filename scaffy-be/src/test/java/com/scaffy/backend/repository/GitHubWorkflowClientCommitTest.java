package com.scaffy.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
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

class GitHubWorkflowClientCommitTest {

	private HttpClient httpClient;
	private ProviderTokenCrypto crypto;
	private WorkspaceOAuthTokenRepository tokenRepository;
	private GitHubWorkflowClient client;

	private final UUID workspaceId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		httpClient = mock(HttpClient.class);
		crypto = mock(ProviderTokenCrypto.class);
		tokenRepository = mock(WorkspaceOAuthTokenRepository.class);
		client = new GitHubWorkflowClient(httpClient, new ObjectMapper(), crypto, tokenRepository);

		OAuthAccessTokenRecord token = new OAuthAccessTokenRecord(
				"encrypted-token",
				OffsetDateTime.now().plusHours(1),
				"repo");
		when(tokenRepository.findToken(workspaceId, userId, "github", "")).thenReturn(Optional.of(token));
		when(crypto.decrypt("encrypted-token")).thenReturn("gha-token");
	}

	@Test
	void commitWorkflowSendsUpdatedFileWithExistingShaAndReturnsCommitMetadata() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("""
				{ "sha": "old-sha", "type": "file" }
				""");
		HttpResponse<String> putResponse = okResponse("""
				{
				  "commit": {
				    "sha": "new-sha",
				    "html_url": "https://github.com/o/r/commit/new-sha"
				  }
				}
				""");

		doReturn(repoMeta, fileMeta, putResponse).when(httpClient).send(any(HttpRequest.class), any());

		WorkflowCommitResult result = client.commitWorkflow(
				workspaceId,
				userId,
				connection(),
				".github/workflows/ci.yml",
				"new yaml",
				"Improve CI/CD pipeline quality");

		assertThat(result.commitSha()).isEqualTo("new-sha");
		assertThat(result.commitUrl()).isEqualTo("https://github.com/o/r/commit/new-sha");
		assertThat(result.branch()).isEqualTo("main");
	}

	@Test
	void commitWorkflowMapsAuthFailuresToConflict() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("""
				{ "sha": "old-sha" }
				""");
		HttpResponse<String> unauthorized = stringResponse(401, "Unauthorized");
		doReturn(repoMeta, fileMeta, unauthorized).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("Reconnect");
	}

	@Test
	void commitWorkflowMapsConflictAndUnprocessableToConflict() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("""
				{ "sha": "old-sha" }
				""");
		HttpResponse<String> conflict = stringResponse(409, "{}");
		doReturn(repoMeta, fileMeta, conflict).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("changed since");
	}

	@Test
	void commitWorkflowFailsWhenFileMetadataHasNoSha() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("{}");
		doReturn(repoMeta, fileMeta).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("sha");
	}

	@Test
	void commitWorkflowFailsWhenRepositoryHasNoDefaultBranch() throws Exception {
		HttpResponse<String> repoMeta = okResponse("{}");
		doReturn(repoMeta).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("metadata");
	}

	@Test
	void commitWorkflowMaps404ToNotFound() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("""
				{ "sha": "old-sha" }
				""");
		HttpResponse<String> notFound = stringResponse(404, "{}");
		doReturn(repoMeta, fileMeta, notFound).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("not found");
	}

	@Test
	void commitWorkflowMapsGeneric500ToBadGateway() throws Exception {
		HttpResponse<String> repoMeta = okResponse("""
				{ "default_branch": "main" }
				""");
		HttpResponse<String> fileMeta = okResponse("""
				{ "sha": "old-sha" }
				""");
		HttpResponse<String> serverError = stringResponse(500, "server error");
		doReturn(repoMeta, fileMeta, serverError).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("could not be created");
	}

	@Test
	void commitWorkflowPropagatesIoErrorsAsBadGateway() throws Exception {
		doThrow(new IOException("network")).when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.commitWorkflow(
				workspaceId, userId, connection(), ".github/workflows/ci.yml", "yaml", "msg"))
				.isInstanceOf(ResponseStatusException.class);
	}

	private RepositoryConnection connection() {
		return new RepositoryConnection(
				UUID.randomUUID(),
				workspaceId,
				userId,
				"github",
				"",
				"o",
				"r",
				"https://github.com/o/r",
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
