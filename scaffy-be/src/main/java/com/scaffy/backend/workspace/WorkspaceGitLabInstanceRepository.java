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
public class WorkspaceGitLabInstanceRepository {

	private static final String SELECT_COLUMNS =
			"id, workspace_id, registration_id, host, base_url, display_name, created_by_user_id, created_at";

	private final JdbcTemplate jdbcTemplate;

	public WorkspaceGitLabInstanceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public WorkspaceGitLabInstance add(
			UUID workspaceId,
			String registrationId,
			String host,
			String baseUrl,
			String displayName,
			UUID createdByUserId) {
		int updated = jdbcTemplate.update("""
				UPDATE workspace_gitlab_instances
				SET registration_id = ?, base_url = ?, display_name = ?, updated_at = CURRENT_TIMESTAMP
				WHERE workspace_id = ? AND host = ?
				""", registrationId, baseUrl, displayName, workspaceId, host);
		if (updated == 0) {
			jdbcTemplate.update("""
					INSERT INTO workspace_gitlab_instances
						(id, workspace_id, registration_id, host, base_url, display_name, created_by_user_id)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", UUID.randomUUID(), workspaceId, registrationId, host, baseUrl, displayName, createdByUserId);
		}
		return findByWorkspaceAndHost(workspaceId, host).orElseThrow();
	}

	public List<WorkspaceGitLabInstance> listForWorkspace(UUID workspaceId) {
		return jdbcTemplate.query("""
				SELECT %s
				FROM workspace_gitlab_instances
				WHERE workspace_id = ?
				ORDER BY host
				""".formatted(SELECT_COLUMNS), this::map, workspaceId);
	}

	public Optional<WorkspaceGitLabInstance> findByWorkspaceAndHost(UUID workspaceId, String host) {
		return jdbcTemplate.query("""
				SELECT %s
				FROM workspace_gitlab_instances
				WHERE workspace_id = ? AND host = ?
				""".formatted(SELECT_COLUMNS), this::map, workspaceId, host).stream().findFirst();
	}

	public boolean delete(UUID workspaceId, UUID id) {
		return jdbcTemplate.update("""
				DELETE FROM workspace_gitlab_instances
				WHERE workspace_id = ? AND id = ?
				""", workspaceId, id) > 0;
	}

	private WorkspaceGitLabInstance map(ResultSet rs, int rowNum) throws SQLException {
		return new WorkspaceGitLabInstance(
				rs.getObject("id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getString("registration_id"),
				rs.getString("host"),
				rs.getString("base_url"),
				rs.getString("display_name"),
				rs.getObject("created_by_user_id", UUID.class),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
