package com.scaffy.backend.init;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InitGenerationJobRepository {

	private final JdbcTemplate jdbcTemplate;

	public InitGenerationJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public InitGenerationJob insert(
			UUID id,
			UUID userId,
			InitJobRequest request,
			String requestJson,
			String selectionJson) {
		jdbcTemplate.update("""
				INSERT INTO initializer_generation_jobs (
					id,
					user_id,
					status,
					project_name,
					request_json,
					selection_json,
					progress_message
				)
				VALUES (?, ?, 'queued', ?, ?, ?, 'Waiting for generator')
				""", id, userId, request.projectName(), requestJson, selectionJson);
		return findById(id).orElseThrow();
	}

	public Optional<InitGenerationJob> findById(UUID id) {
		return jdbcTemplate.query("""
				SELECT
					id,
					user_id,
					status,
					project_name,
					request_json,
					selection_json,
					progress_message,
					error_message,
					artifact_object_key,
					created_at,
					started_at,
					completed_at
				FROM initializer_generation_jobs
				WHERE id = ?
				""", this::mapJob, id).stream().findFirst();
	}

	public List<InitJobLogLine> findLogs(UUID jobId, int limit) {
		return jdbcTemplate.query("""
				SELECT id, stream, message, created_at
				FROM (
					SELECT id, stream, message, created_at
					FROM initializer_generation_job_logs
					WHERE job_id = ?
					ORDER BY id DESC
					LIMIT ?
				) recent
				ORDER BY id ASC
				""", this::mapLogLine, jobId, limit);
	}

	private InitGenerationJob mapJob(ResultSet rs, int rowNum) throws SQLException {
		return new InitGenerationJob(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("status"),
				rs.getString("project_name"),
				rs.getString("request_json"),
				rs.getString("selection_json"),
				rs.getString("progress_message"),
				rs.getString("error_message"),
				rs.getString("artifact_object_key"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class));
	}

	private InitJobLogLine mapLogLine(ResultSet rs, int rowNum) throws SQLException {
		return new InitJobLogLine(
				rs.getLong("id"),
				rs.getString("stream"),
				rs.getString("message"),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
