package com.scaffy.backend.workspace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkspaceRepository {

	private final JdbcTemplate jdbcTemplate;

	public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public Workspace create(String name, String slug, UUID ownerUserId) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO workspaces (id, name, slug)
				VALUES (?, ?, ?)
				""", id, name, slug);
		addMember(id, ownerUserId, WorkspaceRole.OWNER);
		return findById(id).orElseThrow();
	}

	public void addMember(UUID workspaceId, UUID userId, String role) {
		jdbcTemplate.update("""
				INSERT INTO workspace_members (id, workspace_id, user_id, role)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (workspace_id, user_id) DO NOTHING
				""", UUID.randomUUID(), workspaceId, userId, WorkspaceRole.normalize(role));
	}

	public Optional<Workspace> findById(UUID id) {
		return jdbcTemplate.query("""
				SELECT id, name, slug, created_at
				FROM workspaces
				WHERE id = ?
				""", this::mapWorkspace, id).stream().findFirst();
	}

	public boolean existsBySlug(String slug) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM workspaces WHERE slug = ?", Integer.class, slug);
		return count != null && count > 0;
	}

	public void rename(UUID workspaceId, String name) {
		jdbcTemplate.update("""
				UPDATE workspaces
				SET name = ?, updated_at = CURRENT_TIMESTAMP
				WHERE id = ?
				""", name, workspaceId);
	}

	public List<WorkspaceMembership> listForUser(UUID userId) {
		return jdbcTemplate.query("""
				SELECT w.id, w.name, w.slug, m.role, m.joined_at
				FROM workspace_members m
				JOIN workspaces w ON w.id = m.workspace_id
				WHERE m.user_id = ?
				ORDER BY m.joined_at ASC
				""", (rs, rowNum) -> new WorkspaceMembership(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("slug"),
				rs.getString("role"),
				rs.getObject("joined_at", OffsetDateTime.class)), userId);
	}

	public Optional<String> findRole(UUID workspaceId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT role
				FROM workspace_members
				WHERE workspace_id = ? AND user_id = ?
				""", (rs, rowNum) -> rs.getString("role"), workspaceId, userId).stream().findFirst();
	}

	public List<WorkspaceMember> listMembers(UUID workspaceId) {
		return jdbcTemplate.query("""
				SELECT u.id, u.email, u.display_name, u.avatar_url, m.role, m.joined_at
				FROM workspace_members m
				JOIN users u ON u.id = m.user_id
				WHERE m.workspace_id = ?
				ORDER BY m.joined_at ASC
				""", (rs, rowNum) -> new WorkspaceMember(
				rs.getObject("id", UUID.class),
				rs.getString("email"),
				rs.getString("display_name"),
				rs.getString("avatar_url"),
				rs.getString("role"),
				rs.getObject("joined_at", OffsetDateTime.class)), workspaceId);
	}

	public int countMembers(UUID workspaceId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ?", Integer.class, workspaceId);
		return count == null ? 0 : count;
	}

	public int countOwners(UUID workspaceId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND role = ?",
				Integer.class, workspaceId, WorkspaceRole.OWNER);
		return count == null ? 0 : count;
	}

	public boolean removeMember(UUID workspaceId, UUID userId) {
		return jdbcTemplate.update("""
				DELETE FROM workspace_members
				WHERE workspace_id = ? AND user_id = ?
				""", workspaceId, userId) > 0;
	}

	private Workspace mapWorkspace(ResultSet rs, int rowNum) throws SQLException {
		return new Workspace(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("slug"),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
