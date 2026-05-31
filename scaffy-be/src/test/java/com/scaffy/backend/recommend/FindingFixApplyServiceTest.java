package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.repository.GitHubWorkflowClient;
import com.scaffy.backend.repository.GitLabWorkflowClient;
import com.scaffy.backend.repository.RepositoryAnalysisRepository;
import com.scaffy.backend.repository.RepositoryConnection;
import com.scaffy.backend.repository.RepositoryConnectionRepository;
import com.scaffy.backend.repository.WorkflowCommitResult;

class FindingFixApplyServiceTest {

	private FindingFixRepository findingFixRepository;
	private RepositoryAnalysisRepository analysisRepository;
	private RepositoryConnectionRepository connectionRepository;
	private GitHubWorkflowClient gitHubWorkflowClient;
	private GitLabWorkflowClient gitLabWorkflowClient;
	private FindingFixApplyService service;

	private final UUID userId = UUID.randomUUID();
	private final UUID workspaceId = UUID.randomUUID();
	private final UUID runId = UUID.randomUUID();
	private final UUID connectionId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		findingFixRepository = mock(FindingFixRepository.class);
		analysisRepository = mock(RepositoryAnalysisRepository.class);
		connectionRepository = mock(RepositoryConnectionRepository.class);
		gitHubWorkflowClient = mock(GitHubWorkflowClient.class);
		gitLabWorkflowClient = mock(GitLabWorkflowClient.class);
		service = new FindingFixApplyService(
				findingFixRepository,
				analysisRepository,
				connectionRepository,
				gitHubWorkflowClient,
				gitLabWorkflowClient);
	}

	@Test
	void returnsErrorWhenRequestIsMissingFields() {
		FindingFixApplyResponse response = service.apply(
				userId,
				workspaceId,
				new FindingFixApplyRequest(null, null, null, null, null));

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.message()).contains("required");
	}

	private FindingFixRequest.Finding sampleFinding() {
		return new FindingFixRequest.Finding(
				"MISSING_TIMEOUT",
				"Missing timeout",
				"Each job should set timeout-minutes",
				"workflow_quality",
				"Execution safety",
				"SMELL",
				"timeout-minutes not set",
				"jobs.build",
				null,
				null);
	}

	@Test
	void returnsErrorWhenAnalysisRunIsUnknown() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.empty());

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.message()).contains("Unknown");
	}

	@Test
	void returnsUnavailableWhenConnectionIsNotInWorkspace() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId)).thenReturn(Optional.empty());

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.UNAVAILABLE);
		verify(findingFixRepository, never()).markCommitted(any(), any(), any(), any(), any());
	}

	@Test
	void commitsViaGitHubAndPersistsResult() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("github")));
		WorkflowCommitResult result = new WorkflowCommitResult("abc123", "https://github.com/o/r/commit/abc123", "main");
		when(gitHubWorkflowClient.commitWorkflow(
				eq(workspaceId), eq(userId), any(), eq(".github/workflows/ci.yml"), eq("new content"),
				eq("Improve CI/CD pipeline quality")))
				.thenReturn(result);

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.OK);
		assertThat(response.commitSha()).isEqualTo("abc123");
		assertThat(response.commitUrl()).isEqualTo("https://github.com/o/r/commit/abc123");
		assertThat(response.branch()).isEqualTo("main");
		verify(findingFixRepository, times(1))
				.markCommitted(eq(runId), any(String.class), eq("abc123"),
						eq("https://github.com/o/r/commit/abc123"), eq("main"));
		verify(gitLabWorkflowClient, never()).commitWorkflow(any(), any(), any(), any(), any(), any());
	}

	@Test
	void commitsViaGitLabWhenConnectionIsGitLab() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("gitlab")));
		WorkflowCommitResult result = new WorkflowCommitResult("def456", "https://gitlab.com/o/r/-/commit/def456", "main");
		when(gitLabWorkflowClient.commitWorkflow(
				eq(workspaceId), eq(userId), any(), eq(".github/workflows/ci.yml"), eq("new content"),
				eq("Improve CI/CD pipeline quality")))
				.thenReturn(result);

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.OK);
		assertThat(response.commitSha()).isEqualTo("def456");
		verify(gitHubWorkflowClient, never()).commitWorkflow(any(), any(), any(), any(), any(), any());
	}

	@Test
	void usesProvidedCommitMessageWhenSet() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("github")));
		WorkflowCommitResult result = new WorkflowCommitResult("abc", "url", "main");
		when(gitHubWorkflowClient.commitWorkflow(any(), any(), any(), any(), any(), eq("My custom message")))
				.thenReturn(result);

		FindingFixApplyResponse response = service.apply(userId, workspaceId, new FindingFixApplyRequest(
				runId, sampleFinding(), ".github/workflows/ci.yml", "new content", "  My custom message  "));

		assertThat(response.status()).isEqualTo(RecommendationStatus.OK);
		verify(gitHubWorkflowClient, times(1))
				.commitWorkflow(any(), any(), any(), any(), any(), eq("My custom message"));
	}

	@Test
	void mapsConflictResponseStatusToUnavailable() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("github")));
		when(gitHubWorkflowClient.commitWorkflow(any(), any(), any(), any(), any(), any()))
				.thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Reconnect GitHub"));

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.UNAVAILABLE);
		assertThat(response.message()).contains("Reconnect GitHub");
		verify(findingFixRepository, never()).markCommitted(any(), any(), any(), any(), any());
	}

	@Test
	void mapsOtherProviderErrorsToError() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("github")));
		when(gitHubWorkflowClient.commitWorkflow(any(), any(), any(), any(), any(), any()))
				.thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream down"));

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.message()).contains("upstream down");
	}

	@Test
	void rejectsUnsupportedProvider() {
		when(analysisRepository.findRunConnectionId(runId)).thenReturn(Optional.of(connectionId));
		when(connectionRepository.findByIdForWorkspace(workspaceId, connectionId))
				.thenReturn(Optional.of(connection("bitbucket")));

		FindingFixApplyResponse response = service.apply(userId, workspaceId, validRequest());

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.message()).contains("bitbucket");
	}

	private FindingFixApplyRequest validRequest() {
		return new FindingFixApplyRequest(runId, sampleFinding(), ".github/workflows/ci.yml", "new content", null);
	}

	private RepositoryConnection connection(String provider) {
		return new RepositoryConnection(
				connectionId,
				workspaceId,
				userId,
				provider,
				provider.equals("gitlab") ? "gitlab.com" : "",
				"o",
				"r",
				"https://example.test/o/r",
				OffsetDateTime.now());
	}
}
