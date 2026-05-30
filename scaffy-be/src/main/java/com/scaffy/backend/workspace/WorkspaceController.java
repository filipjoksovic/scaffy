package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.auth.ScaffyPrincipal;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService workspaceService;

	public WorkspaceController(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<WorkspaceResponse> list(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return workspaceService.listForUser(principal.userId())
				.stream()
				.map(WorkspaceResponse::from)
				.toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public WorkspaceResponse create(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestBody CreateWorkspaceRequest request) {
		Workspace workspace = workspaceService.create(principal.userId(), request.name());
		return new WorkspaceResponse(workspace.id().toString(), workspace.name(), workspace.slug(),
				WorkspaceRole.OWNER, workspace.createdAt());
	}

	@GetMapping(path = "/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<InvitationResponse> myInvitations(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return workspaceService.listMyInvitations(principal.user())
				.stream()
				.map(InvitationResponse::from)
				.toList();
	}

	@PostMapping(path = "/invitations/{token}/accept", produces = MediaType.APPLICATION_JSON_VALUE)
	public WorkspaceResponse acceptInvitation(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable String token) {
		Workspace workspace = workspaceService.acceptInvitation(principal.user(), token);
		String role = workspaceService.requireMembership(workspace.id(), principal.userId());
		return new WorkspaceResponse(workspace.id().toString(), workspace.name(), workspace.slug(),
				role, workspace.createdAt());
	}

	@GetMapping(path = "/{workspaceId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public WorkspaceDetailResponse get(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId) {
		Workspace workspace = workspaceService.getForMember(workspaceId, principal.userId());
		String role = workspaceService.requireMembership(workspaceId, principal.userId());
		List<MemberResponse> members = workspaceService.listMembers(workspaceId, principal.userId())
				.stream()
				.map(MemberResponse::from)
				.toList();
		List<InvitationResponse> invitations = WorkspaceRole.isOwner(role)
				? workspaceService.listPendingInvitations(workspaceId, principal.userId())
						.stream()
						.map(InvitationResponse::from)
						.toList()
				: List.of();
		return new WorkspaceDetailResponse(
				workspace.id().toString(),
				workspace.name(),
				workspace.slug(),
				role,
				workspace.createdAt(),
				members,
				invitations);
	}

	@PatchMapping(path = "/{workspaceId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public WorkspaceResponse rename(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@RequestBody CreateWorkspaceRequest request) {
		Workspace workspace = workspaceService.rename(workspaceId, principal.userId(), request.name());
		String role = workspaceService.requireMembership(workspaceId, principal.userId());
		return new WorkspaceResponse(workspace.id().toString(), workspace.name(), workspace.slug(),
				role, workspace.createdAt());
	}

	@GetMapping(path = "/{workspaceId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<MemberResponse> members(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId) {
		return workspaceService.listMembers(workspaceId, principal.userId())
				.stream()
				.map(MemberResponse::from)
				.toList();
	}

	@DeleteMapping("/{workspaceId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@PathVariable UUID userId) {
		workspaceService.removeMember(workspaceId, principal.userId(), userId);
	}

	@PostMapping(path = "/{workspaceId}/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationResponse invite(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@RequestBody InviteRequest request) {
		return InvitationResponse.from(
				workspaceService.invite(workspaceId, principal.userId(), request.email(), request.role()));
	}

	@GetMapping(path = "/{workspaceId}/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<InvitationResponse> pendingInvitations(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId) {
		return workspaceService.listPendingInvitations(workspaceId, principal.userId())
				.stream()
				.map(InvitationResponse::from)
				.toList();
	}

	@DeleteMapping("/{workspaceId}/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revokeInvitation(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@PathVariable UUID invitationId) {
		workspaceService.revokeInvitation(workspaceId, principal.userId(), invitationId);
	}

	public record CreateWorkspaceRequest(String name) {
	}

	public record InviteRequest(String email, String role) {
	}

	public record WorkspaceResponse(
			String id,
			String name,
			String slug,
			String role,
			OffsetDateTime createdAt) {

		static WorkspaceResponse from(WorkspaceMembership membership) {
			return new WorkspaceResponse(
					membership.workspaceId().toString(),
					membership.name(),
					membership.slug(),
					membership.role(),
					membership.joinedAt());
		}
	}

	public record WorkspaceDetailResponse(
			String id,
			String name,
			String slug,
			String role,
			OffsetDateTime createdAt,
			List<MemberResponse> members,
			List<InvitationResponse> invitations) {
	}

	public record MemberResponse(
			String userId,
			String email,
			String displayName,
			String avatarUrl,
			String role,
			OffsetDateTime joinedAt) {

		static MemberResponse from(WorkspaceMember member) {
			return new MemberResponse(
					member.userId().toString(),
					member.email(),
					member.displayName(),
					member.avatarUrl(),
					member.role(),
					member.joinedAt());
		}
	}

	public record InvitationResponse(
			String id,
			String workspaceId,
			String workspaceName,
			String email,
			String role,
			String token,
			OffsetDateTime expiresAt) {

		static InvitationResponse from(WorkspaceInvitation invitation) {
			return new InvitationResponse(
					invitation.id().toString(),
					invitation.workspaceId().toString(),
					invitation.workspaceName(),
					invitation.email(),
					invitation.role(),
					invitation.token(),
					invitation.expiresAt());
		}
	}
}
