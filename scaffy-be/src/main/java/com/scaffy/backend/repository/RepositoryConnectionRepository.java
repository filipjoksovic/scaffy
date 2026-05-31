package com.scaffy.backend.repository;

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
public class RepositoryConnectionRepository {

	private static final String SELECT_COLUMNS =
			"id, workspace_id, user_id, provider, provider_instance, "
					+ "repository_owner, repository_name, repository_url, connected_at";

	private final JdbcTemplate jdbcTemplate;

	public RepositoryConnectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<RepositoryConnection> findByWorkspaceId(UUID workspaceId) {
		return jdbcTemplate.query("""
				SELECT %s
				FROM repository_connections
				WHERE workspace_id = ?
				ORDER BY connected_at DESC
				""".formatted(SELECT_COLUMNS), this::mapConnection, workspaceId);
	}

	@Transactional
	public RepositoryConnection connect(
			UUID workspaceId,
			UUID connectorUserId,
			String provider,
			String providerInstance,
			String owner,
			String name,
			String url) {
		String instance = providerInstance == null ? "" : providerInstance;
		Optional<RepositoryConnection> existing =
				findByWorkspaceAndRepository(workspaceId, provider, instance, owner, name);
		if (existing.isPresent()) {
			RepositoryConnection connection = existing.get();
			jdbcTemplate.update("""
					UPDATE repository_connections
					SET repository_url = ?, user_id = ?, updated_at = CURRENT_TIMESTAMP
					WHERE id = ?
					""", url, connectorUserId, connection.id());
			return findByIdForWorkspace(workspaceId, connection.id()).orElse(connection);
		}

		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO repository_connections (
					id,
					workspace_id,
					user_id,
					provider,
					provider_instance,
					repository_owner,
					repository_name,
					repository_url
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", id, workspaceId, connectorUserId, provider, instance, owner, name, url);
		return findByIdForWorkspace(workspaceId, id).orElseThrow();
	}

	public boolean deleteForWorkspace(UUID workspaceId, UUID id) {
		return jdbcTemplate.update("""
				DELETE FROM repository_connections
				WHERE id = ? AND workspace_id = ?
				""", id, workspaceId) > 0;
	}

	public Optional<RepositoryConnection> findByIdForWorkspace(UUID workspaceId, UUID id) {
		return jdbcTemplate.query("""
				SELECT %s
				FROM repository_connections
				WHERE workspace_id = ? AND id = ?
				""".formatted(SELECT_COLUMNS), this::mapConnection, workspaceId, id).stream().findFirst();
	}

	private Optional<RepositoryConnection> findByWorkspaceAndRepository(
			UUID workspaceId,
			String provider,
			String providerInstance,
			String owner,
			String name) {
		return jdbcTemplate.query("""
				SELECT %s
				FROM repository_connections
				WHERE workspace_id = ? AND provider = ? AND provider_instance = ?
				    AND repository_owner = ? AND repository_name = ?
				""".formatted(SELECT_COLUMNS), this::mapConnection,
				workspaceId, provider, providerInstance, owner, name).stream().findFirst();
	}

	private RepositoryConnection mapConnection(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryConnection(
				rs.getObject("id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("provider"),
				rs.getString("provider_instance"),
				rs.getString("repository_owner"),
				rs.getString("repository_name"),
				rs.getString("repository_url"),
				rs.getObject("connected_at", OffsetDateTime.class));
	}
}
