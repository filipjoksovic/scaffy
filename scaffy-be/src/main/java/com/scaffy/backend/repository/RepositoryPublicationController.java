package com.scaffy.backend.repository;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.workspace.WorkspaceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories/github/publications")
public class RepositoryPublicationController {

	private static final String WORKSPACE_HEADER = "X-Workspace-Id";

	private final RepositoryPublicationService publicationService;
	private final WorkspaceService workspaceService;

	public RepositoryPublicationController(
			RepositoryPublicationService publicationService,
			WorkspaceService workspaceService) {
		this.publicationService = publicationService;
		this.workspaceService = workspaceService;
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public RepositoryPublicationResponse create(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@Valid @RequestBody CreateRepositoryPublicationRequest request) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		return publicationService.create(principal.userId(), activeWorkspace, request);
	}

	@GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryPublicationResponse get(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		return publicationService.get(principal.userId(), id);
	}
}
