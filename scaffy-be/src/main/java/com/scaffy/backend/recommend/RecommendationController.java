package com.scaffy.backend.recommend;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.workspace.WorkspaceService;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

	static final String WORKSPACE_HEADER = "X-Workspace-Id";

	private final RecommendationService recommendationService;
	private final FindingFixApplyService findingFixApplyService;
	private final WorkspaceService workspaceService;

	public RecommendationController(
			RecommendationService recommendationService,
			FindingFixApplyService findingFixApplyService,
			WorkspaceService workspaceService) {
		this.recommendationService = recommendationService;
		this.findingFixApplyService = findingFixApplyService;
		this.workspaceService = workspaceService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public RecommendationResponse recommend(@RequestBody AnalysisResponse analysis) {
		return recommendationService.recommend(analysis);
	}

	@PostMapping(path = "/finding", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public FindingFixResponse recommendFix(@RequestBody FindingFixRequest request) {
		return recommendationService.recommendFix(request);
	}

	@PostMapping(path = "/finding/apply", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public FindingFixApplyResponse applyFix(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@RequestBody FindingFixApplyRequest request) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		return findingFixApplyService.apply(principal.userId(), activeWorkspace, request);
	}
}
