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

	private static final String GITHUB = "github";

	private final JdbcTemplate jdbcTemplate;

	public RepositoryConnectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<RepositoryConnection> findByUserId(UUID userId) {
		return jdbcTemplate.query("""
				SELECT id, user_id, provider, repository_owner, repository_name, repository_url, connected_at
				FROM repository_connections
				WHERE user_id = ?
				ORDER BY connected_at DESC
				""", this::mapConnection, userId);
	}

	@Transactional
	public RepositoryConnection connectGitHub(UUID userId, GitHubRepositoryRef ref) {
		Optional<RepositoryConnection> existing = findByUserAndRepository(userId, GITHUB, ref.owner(), ref.name());
		if (existing.isPresent()) {
			RepositoryConnection connection = existing.get();
			jdbcTemplate.update("""
					UPDATE repository_connections
					SET repository_url = ?, updated_at = CURRENT_TIMESTAMP
					WHERE id = ?
					""", ref.url(), connection.id());
			return findByIdForUser(userId, connection.id()).orElse(connection);
		}

		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO repository_connections (
					id,
					user_id,
					provider,
					repository_owner,
					repository_name,
					repository_url
				)
				VALUES (?, ?, ?, ?, ?, ?)
				""", id, userId, GITHUB, ref.owner(), ref.name(), ref.url());
		return findByIdForUser(userId, id).orElseThrow();
	}

	public boolean deleteForUser(UUID userId, UUID id) {
		return jdbcTemplate.update("""
				DELETE FROM repository_connections
				WHERE id = ? AND user_id = ?
				""", id, userId) > 0;
	}

	public Optional<RepositoryConnection> findByIdForUser(UUID userId, UUID id) {
		return jdbcTemplate.query("""
				SELECT id, user_id, provider, repository_owner, repository_name, repository_url, connected_at
				FROM repository_connections
				WHERE user_id = ? AND id = ?
				""", this::mapConnection, userId, id).stream().findFirst();
	}

	private Optional<RepositoryConnection> findByUserAndRepository(
			UUID userId,
			String provider,
			String owner,
			String name) {
		return jdbcTemplate.query("""
				SELECT id, user_id, provider, repository_owner, repository_name, repository_url, connected_at
				FROM repository_connections
				WHERE user_id = ? AND provider = ? AND repository_owner = ? AND repository_name = ?
				""", this::mapConnection, userId, provider, owner, name).stream().findFirst();
	}

	private RepositoryConnection mapConnection(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryConnection(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("provider"),
				rs.getString("repository_owner"),
				rs.getString("repository_name"),
				rs.getString("repository_url"),
				rs.getObject("connected_at", OffsetDateTime.class));
	}
}
