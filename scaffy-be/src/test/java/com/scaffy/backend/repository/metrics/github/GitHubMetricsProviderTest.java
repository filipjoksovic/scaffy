package com.scaffy.backend.repository.metrics.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.scaffy.backend.auth.OAuthAccessTokenRecord;
import com.scaffy.backend.auth.ProviderTokenCrypto;
import com.scaffy.backend.auth.WorkspaceOAuthTokenRepository;
import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.MetricsStatus;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;
import com.scaffy.backend.repository.metrics.github.GitHubActionsApiClient.ApiCallOutcome;
import com.scaffy.backend.repository.metrics.github.GitHubActionsApiClient.WorkflowRun;
import com.scaffy.backend.repository.metrics.github.GitHubActionsApiClient.WorkflowRunsResponse;

class GitHubMetricsProviderTest {

	@Test
	void expiredMetadataStillUsesTokenAndFetchesFromApi() {
		GitHubActionsApiClient apiClient = mock(GitHubActionsApiClient.class);
		WorkspaceOAuthTokenRepository tokenRepository = mock(WorkspaceOAuthTokenRepository.class);
		ProviderTokenCrypto tokenCrypto = mock(ProviderTokenCrypto.class);
		GitHubMetricsProvider provider = new GitHubMetricsProvider(apiClient, tokenRepository, tokenCrypto);

		UUID workspaceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		MetricsRequest request = new MetricsRequest(
				workspaceId,
				userId,
				"github-actions",
				"",
				"scaffy-labs",
				"demo-app",
				".github/workflows/ci.yml",
				30);

		when(tokenRepository.findToken(workspaceId, userId, "github", ""))
				.thenReturn(Optional.of(new OAuthAccessTokenRecord(
						"encrypted-token",
						OffsetDateTime.now().minusHours(1),
						"repo workflow")));
		when(tokenCrypto.decrypt("encrypted-token")).thenReturn("gh-token");
		when(apiClient.listWorkflowRuns("scaffy-labs", "demo-app", ".github/workflows/ci.yml", "gh-token", 100, 5))
				.thenReturn(new ApiCallOutcome.NotFound());

		WorkflowMetricsResult result = provider.fetchMetrics(request);

		assertThat(result.status()).isEqualTo(MetricsStatus.WORKFLOW_NOT_FOUND);
		verify(apiClient).listWorkflowRuns("scaffy-labs", "demo-app", ".github/workflows/ci.yml", "gh-token", 100, 5);
	}

	@Test
	void aggregateReturnsCorrectMetricsFromSampleRuns() {
		GitHubActionsApiClient apiClient = mock(GitHubActionsApiClient.class);
		WorkspaceOAuthTokenRepository tokenRepository = mock(WorkspaceOAuthTokenRepository.class);
		ProviderTokenCrypto tokenCrypto = mock(ProviderTokenCrypto.class);
		GitHubMetricsProvider provider = new GitHubMetricsProvider(apiClient, tokenRepository, tokenCrypto);

		UUID workspaceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		MetricsRequest request = new MetricsRequest(
				workspaceId,
				userId,
				"github-actions",
				"",
				"scaffy-labs",
				"demo-app",
				".github/workflows/ci.yml",
				30);

		when(tokenRepository.findToken(workspaceId, userId, "github", ""))
				.thenReturn(Optional.of(new OAuthAccessTokenRecord(
						"encrypted-token",
						OffsetDateTime.now().plusHours(1),
						"repo")));
		when(tokenCrypto.decrypt("encrypted-token")).thenReturn("gh-token");

		Instant now = Instant.now();
		List<WorkflowRun> runs = List.of(
				new WorkflowRun(101L, "Build main", "completed", "success", "push", "main",
						now.minusSeconds(3600), now.minusSeconds(3300), 1L, "CI"),
				new WorkflowRun(102L, "Deploy main", "completed", "success", "workflow_dispatch", "main",
						now.minusSeconds(7200), now.minusSeconds(6900), 1L, "Deploy"),
				new WorkflowRun(103L, "Build feature", "completed", "success", "pull_request", "feature/x",
						now.minusSeconds(10800), now.minusSeconds(10440), 1L, "CI"),
				new WorkflowRun(104L, "Build bugfix", "completed", "failure", "push", "bugfix/y",
						now.minusSeconds(14400), now.minusSeconds(14040), 1L, "CI"),
				new WorkflowRun(105L, "Cancelled run", "completed", "cancelled", "push", "main",
						now.minusSeconds(18000), now.minusSeconds(17880), 1L, "CI"));

		when(apiClient.listWorkflowRuns("scaffy-labs", "demo-app", ".github/workflows/ci.yml", "gh-token", 100, 5))
				.thenReturn(new ApiCallOutcome.Success(new WorkflowRunsResponse(runs, runs.size(), false)));

		WorkflowMetricsResult result = provider.fetchMetrics(request);

		assertThat(result.status()).isEqualTo(MetricsStatus.AVAILABLE);
		assertThat(result.metrics()).isNotNull();
		assertThat(result.metrics().totalRuns()).isEqualTo(5);
		assertThat(result.metrics().successCount()).isEqualTo(3);
		assertThat(result.metrics().failureRate()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.0001));
		assertThat(result.metrics().triggerDistribution()).isNotEmpty();
		assertThat(result.metrics().recentRuns()).isNotEmpty();
	}
}