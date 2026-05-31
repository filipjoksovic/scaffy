package com.scaffy.backend.workspace;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.AppUser;

@Service
public class WorkspaceService {

	private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
	private static final long INVITATION_TTL_DAYS = 14;

	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceInvitationRepository invitationRepository;

	public WorkspaceService(
			WorkspaceRepository workspaceRepository,
			WorkspaceInvitationRepository invitationRepository) {
		this.workspaceRepository = workspaceRepository;
		this.invitationRepository = invitationRepository;
	}

	/** Ensures the user owns at least one workspace, and accepts any invitations addressed to their email. */
	@Transactional
	public void onLogin(AppUser user) {
		if (workspaceRepository.listForUser(user.id()).isEmpty()) {
			String label = displayLabel(user);
			workspaceRepository.create(label + "'s workspace", uniqueSlug(label), user.id());
			log.info("Created personal workspace for userId={}", user.id());
		}
		acceptPendingInvitations(user);
	}

	private void acceptPendingInvitations(AppUser user) {
		if (user.email() == null || user.email().isBlank()) {
			return;
		}
		for (WorkspaceInvitation invitation : invitationRepository.listPendingForEmail(user.email())) {
			workspaceRepository.addMember(invitation.workspaceId(), user.id(), invitation.role());
			invitationRepository.markAccepted(invitation.id());
			log.info("Auto-accepted workspace invitation workspaceId={} userId={}", invitation.workspaceId(), user.id());
		}
	}

	public List<WorkspaceMembership> listForUser(UUID userId) {
		return workspaceRepository.listForUser(userId);
	}

	@Transactional
	public Workspace create(UUID userId, String name) {
		String trimmed = requireText(name, "name");
		return workspaceRepository.create(trimmed, uniqueSlug(trimmed), userId);
	}

	/**
	 * Resolves which workspace a request operates on. When a workspace is requested (header), it must be one the
	 * user belongs to; otherwise the user's first workspace is used.
	 */
	public UUID resolveActiveWorkspace(UUID userId, UUID requestedWorkspaceId) {
		if (requestedWorkspaceId != null) {
			requireMembership(requestedWorkspaceId, userId);
			return requestedWorkspaceId;
		}
		return workspaceRepository.listForUser(userId).stream()
				.findFirst()
				.map(WorkspaceMembership::workspaceId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No workspace is available."));
	}

	public String requireMembership(UUID workspaceId, UUID userId) {
		return workspaceRepository.findRole(workspaceId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this workspace."));
	}

	public void requireOwner(UUID workspaceId, UUID userId) {
		if (!WorkspaceRole.isOwner(requireMembership(workspaceId, userId))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only workspace owners can perform this action.");
		}
	}

	public Workspace getForMember(UUID workspaceId, UUID userId) {
		requireMembership(workspaceId, userId);
		return workspaceRepository.findById(workspaceId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
	}

	public List<WorkspaceMember> listMembers(UUID workspaceId, UUID userId) {
		requireMembership(workspaceId, userId);
		return workspaceRepository.listMembers(workspaceId);
	}

	@Transactional
	public Workspace rename(UUID workspaceId, UUID userId, String name) {
		requireOwner(workspaceId, userId);
		workspaceRepository.rename(workspaceId, requireText(name, "name"));
		return workspaceRepository.findById(workspaceId).orElseThrow();
	}

	@Transactional
	public WorkspaceInvitation invite(UUID workspaceId, UUID inviterUserId, String email, String role) {
		requireOwner(workspaceId, inviterUserId);
		String normalizedEmail = requireText(email, "email").toLowerCase(Locale.ROOT);
		OffsetDateTime expiresAt = OffsetDateTime.now().plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS);
		return invitationRepository.create(
				workspaceId,
				normalizedEmail,
				WorkspaceRole.normalize(role),
				UUID.randomUUID().toString().replace("-", ""),
				inviterUserId,
				expiresAt);
	}

	public List<WorkspaceInvitation> listPendingInvitations(UUID workspaceId, UUID userId) {
		requireOwner(workspaceId, userId);
		return invitationRepository.listPendingForWorkspace(workspaceId);
	}

	@Transactional
	public void revokeInvitation(UUID workspaceId, UUID userId, UUID invitationId) {
		requireOwner(workspaceId, userId);
		if (!invitationRepository.delete(workspaceId, invitationId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found.");
		}
	}

	public List<WorkspaceInvitation> listMyInvitations(AppUser user) {
		if (user.email() == null || user.email().isBlank()) {
			return List.of();
		}
		return invitationRepository.listPendingForEmail(user.email());
	}

	@Transactional
	public Workspace acceptInvitation(AppUser user, String token) {
		WorkspaceInvitation invitation = invitationRepository.findByToken(token)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));
		if (!WorkspaceInvitationRepository.STATUS_PENDING.equals(invitation.status())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer valid.");
		}
		if (invitation.expiresAt() != null && invitation.expiresAt().isBefore(OffsetDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has expired.");
		}
		workspaceRepository.addMember(invitation.workspaceId(), user.id(), invitation.role());
		invitationRepository.markAccepted(invitation.id());
		return workspaceRepository.findById(invitation.workspaceId()).orElseThrow();
	}

	@Transactional
	public void removeMember(UUID workspaceId, UUID actingUserId, UUID targetUserId) {
		String actingRole = requireMembership(workspaceId, actingUserId);
		boolean removingSelf = actingUserId.equals(targetUserId);
		if (!removingSelf && !WorkspaceRole.isOwner(actingRole)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only workspace owners can remove members.");
		}
		String targetRole = workspaceRepository.findRole(workspaceId, targetUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
		if (WorkspaceRole.isOwner(targetRole) && workspaceRepository.countOwners(workspaceId) <= 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A workspace must keep at least one owner.");
		}
		workspaceRepository.removeMember(workspaceId, targetUserId);
	}

	private String displayLabel(AppUser user) {
		if (user.displayName() != null && !user.displayName().isBlank()) {
			return user.displayName().trim();
		}
		if (user.email() != null && !user.email().isBlank()) {
			return user.email().trim();
		}
		return "Personal";
	}

	private String uniqueSlug(String label) {
		String collapsed = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
		int start = 0;
		int end = collapsed.length();
		while (start < end && collapsed.charAt(start) == '-') {
			start++;
		}
		while (end > start && collapsed.charAt(end - 1) == '-') {
			end--;
		}
		String base = collapsed.substring(start, end);
		if (base.isBlank()) {
			base = "workspace";
		}
		if (base.length() > 40) {
			base = base.substring(0, 40);
		}
		String candidate = base;
		int suffix = 1;
		while (workspaceRepository.existsBySlug(candidate)) {
			candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
			if (suffix++ > 5) {
				candidate = "workspace-" + UUID.randomUUID().toString().replace("-", "");
				break;
			}
		}
		return candidate;
	}

	private String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
		}
		return value.trim();
	}
}
