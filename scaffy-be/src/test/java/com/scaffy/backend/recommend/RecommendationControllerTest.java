package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.PipelineProvider;
import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.workspace.WorkspaceService;

class RecommendationControllerTest {

	private RecommendationService recommendationService;
	private FindingFixApplyService applyService;
	private WorkspaceService workspaceService;
	private RecommendationController controller;

	@BeforeEach
	void setUp() {
		recommendationService = mock(RecommendationService.class);
		applyService = mock(FindingFixApplyService.class);
		workspaceService = mock(WorkspaceService.class);
		controller = new RecommendationController(recommendationService, applyService, workspaceService);
	}

	@Test
	void recommendDelegatesToServiceAndReturnsResult() {
		RecommendationResponse stubbed = RecommendationResponse.ok(
				"gpt-4o-mini",
				List.of(new Recommendation(
						"Pin actions",
						"Replace floating tags with SHAs.",
						RecommendationPriority.MEDIUM,
						"UNPINNED_ACTION_VERSION smell.",
						"Replace @v4 with the 40-char SHA.")));
		AnalysisResponse input = new AnalysisResponse(
				PipelineProvider.GITHUB_ACTIONS, 0.0, 1, AnalysisStatus.MISSING, List.of());
		when(recommendationService.recommend(input)).thenReturn(stubbed);

		assertThat(controller.recommend(input)).isSameAs(stubbed);
	}

	@Test
	void recommendFixDelegatesToService() {
		FindingFixRequest request = new FindingFixRequest(
				UUID.randomUUID(),
				"github-actions",
				".github/workflows/ci.yml",
				"name: ci",
				new FindingFixRequest.Finding(
						"MISSING_TIMEOUT", "Missing timeout", "desc",
						"workflow_quality", "Execution safety", "SMELL",
						"timeout-minutes not set", "jobs.build", 1, 2));
		FindingFixResponse stubbed = FindingFixResponse.unavailable("not configured");
		when(recommendationService.recommendFix(request)).thenReturn(stubbed);

		assertThat(controller.recommendFix(request)).isSameAs(stubbed);
	}

	@Test
	void applyFixResolvesWorkspaceAndDelegatesToApplyService() {
		UUID userId = UUID.randomUUID();
		UUID workspaceId = UUID.randomUUID();
		UUID activeWorkspace = UUID.randomUUID();
		FindingFixApplyRequest request = new FindingFixApplyRequest(
				UUID.randomUUID(),
				new FindingFixRequest.Finding(
						"MISSING_TIMEOUT", "Missing timeout", "desc",
						"workflow_quality", "Execution safety", "SMELL",
						null, null, null, null),
				".github/workflows/ci.yml",
				"name: ci",
				"Custom message");
		FindingFixApplyResponse stubbed = FindingFixApplyResponse.ok("abc", "https://example.test/abc", "main");
		ScaffyPrincipal principal = principal(userId);

		when(workspaceService.resolveActiveWorkspace(userId, workspaceId)).thenReturn(activeWorkspace);
		when(applyService.apply(eq(userId), eq(activeWorkspace), any())).thenReturn(stubbed);

		FindingFixApplyResponse result = controller.applyFix(principal, workspaceId, request);

		assertThat(result).isSameAs(stubbed);
		verify(workspaceService).resolveActiveWorkspace(userId, workspaceId);
		verify(applyService).apply(userId, activeWorkspace, request);
	}

	private ScaffyPrincipal principal(UUID userId) {
		return new ScaffyPrincipal(userId, "tester@example.test", "Tester", null);
	}
}
