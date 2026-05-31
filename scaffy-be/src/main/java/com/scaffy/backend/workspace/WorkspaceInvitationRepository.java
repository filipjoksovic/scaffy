package com.scaffy.backend.workspace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceInvitationRepository {

	public static final String STATUS_PENDING = "pending";
	public static final String STATUS_ACCEPTED = "accepted";

	private static final String BASE_SELECT = """
			SELECT i.id, i.workspace_id, w.name AS workspace_name, i.email, i.role, i.token,
			    i.status, i.created_at, i.expires_at
			FROM workspace_invitations i
			JOIN workspaces w ON w.id = i.workspace_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public WorkspaceInvitationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public WorkspaceInvitation create(
			UUID workspaceId,
			String email,
			String role,
			String token,
			UUID invitedByUserId,
			OffsetDateTime expiresAt) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO workspace_invitations
				    (id, workspace_id, email, role, token, invited_by_user_id, status, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)
				ON CONFLICT (workspace_id, email) DO UPDATE
				    SET role = EXCLUDED.role,
				        token = EXCLUDED.token,
				        invited_by_user_id = EXCLUDED.invited_by_user_id,
				        status = 'pending',
				        created_at = CURRENT_TIMESTAMP,
				        expires_at = EXCLUDED.expires_at,
				        accepted_at = NULL
				""", id, workspaceId, email, WorkspaceRole.normalize(role), token, invitedByUserId, expiresAt);
		return findByWorkspaceAndEmail(workspaceId, email).orElseThrow();
	}

	public Optional<WorkspaceInvitation> findByToken(String token) {
		return jdbcTemplate.query(BASE_SELECT + " WHERE i.token = ?", this::mapInvitation, token)
				.stream().findFirst();
	}

	public Optional<WorkspaceInvitation> findById(UUID id) {
		return jdbcTemplate.query(BASE_SELECT + " WHERE i.id = ?", this::mapInvitation, id)
				.stream().findFirst();
	}

	private Optional<WorkspaceInvitation> findByWorkspaceAndEmail(UUID workspaceId, String email) {
		return jdbcTemplate.query(
				BASE_SELECT + " WHERE i.workspace_id = ? AND i.email = ?",
				this::mapInvitation, workspaceId, email).stream().findFirst();
	}

	public List<WorkspaceInvitation> listPendingForWorkspace(UUID workspaceId) {
		return jdbcTemplate.query(
				BASE_SELECT + " WHERE i.workspace_id = ? AND i.status = 'pending' ORDER BY i.created_at DESC",
				this::mapInvitation, workspaceId);
	}

	public List<WorkspaceInvitation> listPendingForEmail(String email) {
		return jdbcTemplate.query(
				BASE_SELECT + " WHERE LOWER(i.email) = LOWER(?) AND i.status = 'pending'"
						+ " AND i.expires_at > CURRENT_TIMESTAMP ORDER BY i.created_at DESC",
				this::mapInvitation, email);
	}

	public void markAccepted(UUID id) {
		jdbcTemplate.update("""
				UPDATE workspace_invitations
				SET status = 'accepted', accepted_at = CURRENT_TIMESTAMP
				WHERE id = ?
				""", id);
	}

	public boolean delete(UUID workspaceId, UUID id) {
		return jdbcTemplate.update("""
				DELETE FROM workspace_invitations
				WHERE id = ? AND workspace_id = ?
				""", id, workspaceId) > 0;
	}

	private WorkspaceInvitation mapInvitation(ResultSet rs, int rowNum) throws SQLException {
		return new WorkspaceInvitation(
				rs.getObject("id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getString("workspace_name"),
				rs.getString("email"),
				rs.getString("role"),
				rs.getString("token"),
				rs.getString("status"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("expires_at", OffsetDateTime.class));
	}
}
