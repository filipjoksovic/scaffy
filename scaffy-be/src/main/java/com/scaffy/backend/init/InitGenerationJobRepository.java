package com.scaffy.backend.init;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
			String selectionJson,
			String idempotencyKey) {
		jdbcTemplate.update("""
				INSERT INTO initializer_generation_jobs (
					id,
					user_id,
					status,
					project_name,
					request_json,
					selection_json,
					progress_message,
					idempotency_key
				)
				VALUES (?, ?, 'queued', ?, ?, ?, 'Waiting for generator', ?)
				""", id, userId, request.projectName(), requestJson, selectionJson, idempotencyKey);
		return findById(id).orElseThrow();
	}

	public int countActiveByUser(UUID userId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM initializer_generation_jobs
				WHERE user_id = ?
					AND status IN ('queued', 'running')
				""", Integer.class, userId);
		return count == null ? 0 : count;
	}

	public Optional<InitGenerationJob> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
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
				WHERE user_id = ?
					AND idempotency_key = ?
				""", this::mapJob, userId, idempotencyKey).stream().findFirst();
	}

	/**
	 * Reclaims jobs whose worker stopped heart-beating but still have attempts left.
	 * Returns the ids so the caller can re-enqueue them; {@code next_attempt_at} is left
	 * NULL so the reaper's due-retry sweep will not push them a second time.
	 */
	public List<UUID> requeueStaleRunning(Duration lease) {
		return jdbcTemplate.queryForList("""
				UPDATE initializer_generation_jobs
				SET status = 'queued',
					progress_message = 'Requeued after worker timeout',
					heartbeat_at = NULL,
					next_attempt_at = NULL
				WHERE status = 'running'
					AND attempt_count < max_attempts
					AND COALESCE(heartbeat_at, started_at) < CURRENT_TIMESTAMP - (? || ' seconds')::interval
				RETURNING id
				""", UUID.class, lease.toSeconds());
	}

	/**
	 * Fails jobs whose worker stopped heart-beating and have no attempts left.
	 */
	public int failExhaustedStaleRunning(Duration lease) {
		return jdbcTemplate.update("""
				UPDATE initializer_generation_jobs
				SET status = 'failed',
					progress_message = 'Generation timed out',
					error_message = 'Worker stopped reporting progress and no retry attempts remained.',
					completed_at = CURRENT_TIMESTAMP
				WHERE status = 'running'
					AND attempt_count >= max_attempts
					AND COALESCE(heartbeat_at, started_at) < CURRENT_TIMESTAMP - (? || ' seconds')::interval
				""", lease.toSeconds());
	}

	/**
	 * Atomically claims queued retries whose backoff window has elapsed, clearing
	 * {@code next_attempt_at} so concurrent reapers cannot push the same job twice.
	 */
	public List<UUID> claimDueRetries() {
		return jdbcTemplate.queryForList("""
				UPDATE initializer_generation_jobs
				SET next_attempt_at = NULL
				WHERE status = 'queued'
					AND next_attempt_at IS NOT NULL
					AND next_attempt_at <= CURRENT_TIMESTAMP
				RETURNING id
				""", UUID.class);
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

	public List<InitGenerationJob> findRecentByUserId(UUID userId, int limit) {
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
				WHERE user_id = ?
				ORDER BY created_at DESC
				LIMIT ?
				""", this::mapJob, userId, limit);
	}

	public List<InitJobLogLine> findLogs(UUID jobId, int limit) {
		return jdbcTemplate.query(String.join("\n",
				"SELECT id, stream, message, created_at",
				"FROM (",
				"  SELECT id, stream, message, created_at",
				"  FROM initializer_generation_job_logs",
				"  WHERE job_id = ?",
				"  ORDER BY id DESC",
				"  LIMIT ?",
				") recent",
				"ORDER BY id ASC"), this::mapLogLine, jobId, limit);
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
