package com.scaffy.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositoryPublicationJobRepository {

	private final JdbcTemplate jdbcTemplate;

	public RepositoryPublicationJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public RepositoryPublicationJob insert(
			UUID id,
			UUID userId,
			UUID workspaceId,
			UUID initJobId,
			String repositoryName,
			String description) {
		jdbcTemplate.update("""
				INSERT INTO repository_publication_jobs (
					id,
					user_id,
					workspace_id,
					initializer_generation_job_id,
					provider,
					repository_name,
					repository_description,
					visibility,
					status,
					progress_message
				)
				VALUES (?, ?, ?, ?, 'github', ?, ?, 'private', 'queued', 'Waiting for publisher')
				""", id, userId, workspaceId, initJobId, repositoryName, blankToNull(description));
		return findByIdForUser(userId, id).orElseThrow();
	}

	public Optional<RepositoryPublicationJob> findByIdForUser(UUID userId, UUID id) {
		return jdbcTemplate.query("""
				SELECT
					id,
					user_id,
					workspace_id,
					initializer_generation_job_id,
					provider,
					repository_name,
					repository_description,
					visibility,
					status,
					progress_message,
					error_message,
					repository_owner,
					repository_url,
					repository_connection_id,
					created_at,
					started_at,
					completed_at
				FROM repository_publication_jobs
				WHERE user_id = ? AND id = ?
				""", this::mapJob, userId, id).stream().findFirst();
	}

	public List<RepositoryPublicationJobLogLine> findLogs(UUID jobId, int limit) {
		return jdbcTemplate.query("""
				SELECT id, stream, message, created_at
				FROM (
					SELECT id, stream, message, created_at
					FROM repository_publication_job_logs
					WHERE job_id = ?
					ORDER BY id DESC
					LIMIT ?
				) recent
				ORDER BY id ASC
				""", this::mapLogLine, jobId, limit);
	}

	private RepositoryPublicationJob mapJob(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryPublicationJob(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getObject("initializer_generation_job_id", UUID.class),
				rs.getString("provider"),
				rs.getString("repository_name"),
				rs.getString("repository_description"),
				rs.getString("visibility"),
				rs.getString("status"),
				rs.getString("progress_message"),
				rs.getString("error_message"),
				rs.getString("repository_owner"),
				rs.getString("repository_url"),
				rs.getObject("repository_connection_id", UUID.class),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class));
	}

	private RepositoryPublicationJobLogLine mapLogLine(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryPublicationJobLogLine(
				rs.getLong("id"),
				rs.getString("stream"),
				rs.getString("message"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
