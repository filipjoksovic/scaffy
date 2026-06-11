package com.scaffy.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RepositoryAnalysisJobRepository {

	private final JdbcTemplate jdbcTemplate;

	public RepositoryAnalysisJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public RepositoryAnalysisJob create(
			UUID id,
			UUID workspaceId,
			UUID userId,
			UUID repositoryConnectionId,
			int maxAttempts) {
		jdbcTemplate.update("""
				INSERT INTO repository_analysis_jobs (
				    id,
				    workspace_id,
				    user_id,
				    repository_connection_id,
				    status,
				    progress_message,
				    progress_percent,
				    max_attempts
				)
				VALUES (?, ?, ?, ?, 'queued', 'Waiting for analyzer', 5, ?)
				""", id, workspaceId, userId, repositoryConnectionId, maxAttempts);
		appendLog(id, "system", "Waiting for analyzer");
		return findById(id).orElseThrow();
	}

	public Optional<RepositoryAnalysisJob> findActiveForRepository(UUID repositoryConnectionId) {
		return jdbcTemplate.query("""
				SELECT
				    id,
				    workspace_id,
				    user_id,
				    repository_connection_id,
				    analysis_run_id,
				    status,
				    progress_message,
				    progress_percent,
				    error_message,
				    attempt_count,
				    max_attempts,
				    created_at,
				    started_at,
				    completed_at
				FROM repository_analysis_jobs
				WHERE repository_connection_id = ?
				    AND status IN ('queued', 'running')
				ORDER BY created_at DESC
				LIMIT 1
				""", this::mapJob, repositoryConnectionId).stream().findFirst();
	}

	public Optional<RepositoryAnalysisJob> findByIdForUser(UUID userId, UUID id) {
		return jdbcTemplate.query(baseSelect() + " WHERE user_id = ? AND id = ?", this::mapJob, userId, id)
				.stream().findFirst();
	}

	public Optional<RepositoryAnalysisJob> findById(UUID id) {
		return jdbcTemplate.query(baseSelect() + " WHERE id = ?", this::mapJob, id)
				.stream().findFirst();
	}

	public List<RepositoryAnalysisJob> findActiveByUser(UUID userId) {
		return jdbcTemplate.query(baseSelect() + """
				WHERE user_id = ?
				    AND status IN ('queued', 'running')
				ORDER BY created_at DESC
				""", this::mapJob, userId);
	}

	public List<RepositoryAnalysisJobLogLine> findLogs(UUID jobId, int limit) {
		return jdbcTemplate.query("""
				SELECT id, stream, message, created_at
				FROM (
				    SELECT id, stream, message, created_at
				    FROM repository_analysis_job_logs
				    WHERE job_id = ?
				    ORDER BY id DESC
				    LIMIT ?
				) recent
				ORDER BY id ASC
				""", this::mapLogLine, jobId, limit);
	}

	public boolean claim(UUID id) {
		return jdbcTemplate.update("""
				UPDATE repository_analysis_jobs
				SET status = 'running',
				    progress_message = 'Analyzer claimed the job',
				    progress_percent = 10,
				    attempt_count = attempt_count + 1,
				    heartbeat_at = CURRENT_TIMESTAMP,
				    next_attempt_at = NULL,
				    started_at = COALESCE(started_at, CURRENT_TIMESTAMP)
				WHERE id = ? AND status = 'queued'
				""", id) == 1;
	}

	public void heartbeat(UUID id) {
		jdbcTemplate.update("""
				UPDATE repository_analysis_jobs
				SET heartbeat_at = CURRENT_TIMESTAMP
				WHERE id = ? AND status = 'running'
				""", id);
	}

	@Transactional
	public void progress(UUID id, int percent, String message) {
		jdbcTemplate.update("""
				UPDATE repository_analysis_jobs
				SET progress_message = ?,
				    progress_percent = ?,
				    heartbeat_at = CURRENT_TIMESTAMP
				WHERE id = ? AND status = 'running'
				""", message, percent, id);
		appendLog(id, "system", message);
	}

	@Transactional
	public void succeed(UUID id, UUID analysisRunId) {
		jdbcTemplate.update("""
				UPDATE repository_analysis_jobs
				SET status = 'succeeded',
				    progress_message = 'Analysis complete',
				    progress_percent = 100,
				    analysis_run_id = ?,
				    error_message = NULL,
				    heartbeat_at = NULL,
				    next_attempt_at = NULL,
				    completed_at = CURRENT_TIMESTAMP
				WHERE id = ?
				""", analysisRunId, id);
		appendLog(id, "system", "Analysis complete");
	}

	@Transactional
	public void fail(UUID id, String message, long retryBackoffMs) {
		RepositoryAnalysisJob job = findById(id).orElseThrow();
		String trimmed = truncate(message);
		boolean willRetry = job.attemptCount() < job.maxAttempts();
		if (willRetry) {
			jdbcTemplate.update("""
					UPDATE repository_analysis_jobs
					SET status = 'queued',
					    progress_message = ?,
					    error_message = ?,
					    heartbeat_at = NULL,
					    next_attempt_at = CURRENT_TIMESTAMP + (? || ' milliseconds')::interval
					WHERE id = ?
					""",
					"Retry scheduled (attempt " + job.attemptCount() + "/" + job.maxAttempts() + ")",
					trimmed,
					String.valueOf(retryBackoffMs),
					id);
			appendLog(id, "system", "Retry scheduled after failure: " + trimmed);
		}
		else {
			jdbcTemplate.update("""
					UPDATE repository_analysis_jobs
					SET status = 'failed',
					    progress_message = 'Analysis failed',
					    progress_percent = 100,
					    error_message = ?,
					    heartbeat_at = NULL,
					    next_attempt_at = NULL,
					    completed_at = CURRENT_TIMESTAMP
					WHERE id = ?
					""", trimmed, id);
			appendLog(id, "stderr", trimmed);
		}
	}

	public List<UUID> requeueStaleRunning(Duration lease) {
		return jdbcTemplate.queryForList("""
				UPDATE repository_analysis_jobs
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

	public int failExhaustedStaleRunning(Duration lease) {
		return jdbcTemplate.update("""
				UPDATE repository_analysis_jobs
				SET status = 'failed',
				    progress_message = 'Analysis timed out',
				    progress_percent = 100,
				    error_message = 'Worker stopped reporting progress and no retry attempts remained.',
				    completed_at = CURRENT_TIMESTAMP
				WHERE status = 'running'
				    AND attempt_count >= max_attempts
				    AND COALESCE(heartbeat_at, started_at) < CURRENT_TIMESTAMP - (? || ' seconds')::interval
				""", lease.toSeconds());
	}

	public List<UUID> claimDueRetries() {
		return jdbcTemplate.queryForList("""
				UPDATE repository_analysis_jobs
				SET next_attempt_at = NULL
				WHERE status = 'queued'
				    AND next_attempt_at IS NOT NULL
				    AND next_attempt_at <= CURRENT_TIMESTAMP
				RETURNING id
				""", UUID.class);
	}

	public void appendLog(UUID jobId, String stream, String message) {
		String[] lines = message.split("\\R");
		int start = Math.max(0, lines.length - 200);
		for (int i = start; i < lines.length; i++) {
			String line = lines[i].stripTrailing();
			if (!line.isBlank()) {
				jdbcTemplate.update("""
						INSERT INTO repository_analysis_job_logs (job_id, stream, message)
						VALUES (?, ?, ?)
						""", jobId, stream, line.substring(0, Math.min(line.length(), 4000)));
			}
		}
	}

	private String baseSelect() {
		return """
				SELECT
				    id,
				    workspace_id,
				    user_id,
				    repository_connection_id,
				    analysis_run_id,
				    status,
				    progress_message,
				    progress_percent,
				    error_message,
				    attempt_count,
				    max_attempts,
				    created_at,
				    started_at,
				    completed_at
				FROM repository_analysis_jobs
				""";
	}

	private RepositoryAnalysisJob mapJob(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryAnalysisJob(
				rs.getObject("id", UUID.class),
				rs.getObject("workspace_id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getObject("repository_connection_id", UUID.class),
				rs.getObject("analysis_run_id", UUID.class),
				rs.getString("status"),
				rs.getString("progress_message"),
				rs.getInt("progress_percent"),
				rs.getString("error_message"),
				rs.getInt("attempt_count"),
				rs.getInt("max_attempts"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class));
	}

	private RepositoryAnalysisJobLogLine mapLogLine(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryAnalysisJobLogLine(
				rs.getLong("id"),
				rs.getString("stream"),
				rs.getString("message"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) {
			return "Analysis failed.";
		}
		return value.length() > 4000 ? value.substring(0, 4000) : value;
	}
}
