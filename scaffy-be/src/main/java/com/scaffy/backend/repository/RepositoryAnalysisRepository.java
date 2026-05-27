package com.scaffy.backend.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.scaffy.backend.analyze.AnalysisResponse;

@Repository
public class RepositoryAnalysisRepository {

	public static final int ANALYSIS_SCHEMA_VERSION = 1;
	public static final String ANALYZER_MODEL_VERSION = "capability-analyzer-v1";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public RepositoryAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<PersistedRepositoryAnalysis> findLatestByRepositoryConnectionId(UUID repositoryConnectionId) {
		return jdbcTemplate.query("""
				SELECT id,
					repository_connection_id,
					run_number,
					workflow_path,
					workflow_content_hash,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version,
					analysis_json
				FROM repository_analysis_runs
				WHERE repository_connection_id = ?
				ORDER BY run_number DESC
				LIMIT 1
				""", this::mapPersistedAnalysis, repositoryConnectionId).stream().findFirst();
	}

	public List<PersistedRepositoryAnalysis> findLatestPairByRepositoryConnectionId(UUID repositoryConnectionId) {
		return jdbcTemplate.query("""
				SELECT id,
					repository_connection_id,
					run_number,
					workflow_path,
					workflow_content_hash,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version,
					analysis_json
				FROM repository_analysis_runs
				WHERE repository_connection_id = ?
				ORDER BY run_number DESC
				LIMIT 2
				""", this::mapPersistedAnalysis, repositoryConnectionId);
	}

	public List<RepositoryAnalysisSummary> findSummariesByRepositoryConnectionId(UUID repositoryConnectionId) {
		return jdbcTemplate.query("""
				SELECT id,
					repository_connection_id,
					run_number,
					workflow_path,
					workflow_content_hash,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version
				FROM repository_analysis_runs
				WHERE repository_connection_id = ?
				ORDER BY run_number DESC
				""", this::mapSummary, repositoryConnectionId);
	}

	public Map<UUID, RepositoryAnalysisSummary> findLatestSummariesByRepositoryConnectionIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
		List<RepositoryAnalysisSummary> summaries = jdbcTemplate.query("""
				SELECT r.id,
					r.repository_connection_id,
					r.run_number,
					r.workflow_path,
					r.workflow_content_hash,
					r.provider,
					r.overall_score,
					r.overall_level,
					r.overall_status,
					r.analyzed_at,
					r.analysis_schema_version,
					r.analyzer_model_version
				FROM repository_analysis_runs r
				JOIN (
					SELECT repository_connection_id, MAX(run_number) AS run_number
					FROM repository_analysis_runs
					WHERE repository_connection_id IN (%s)
					GROUP BY repository_connection_id
				) latest
					ON latest.repository_connection_id = r.repository_connection_id
					AND latest.run_number = r.run_number
				""".formatted(placeholders), this::mapSummary, ids.toArray());
		return summaries.stream().collect(Collectors.toMap(RepositoryAnalysisSummary::repositoryConnectionId, summary -> summary));
	}

	public Map<UUID, Integer> countByRepositoryConnectionIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
		return jdbcTemplate.query("""
				SELECT repository_connection_id, COUNT(*) AS analysis_run_count
				FROM repository_analysis_runs
				WHERE repository_connection_id IN (%s)
				GROUP BY repository_connection_id
				""".formatted(placeholders), (rs, rowNum) -> Map.entry(
				rs.getObject("repository_connection_id", UUID.class),
				rs.getInt("analysis_run_count")), ids.toArray())
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Transactional
	public PersistedRepositoryAnalysis insert(UUID repositoryConnectionId, String workflowPath, String workflowContent,
			AnalysisResponse analysis) {
		UUID id = UUID.randomUUID();
		Integer nextRunNumber = jdbcTemplate.queryForObject("""
				SELECT COALESCE(MAX(run_number), 0) + 1
				FROM repository_analysis_runs
				WHERE repository_connection_id = ?
				""", Integer.class, repositoryConnectionId);
		jdbcTemplate.update("""
				INSERT INTO repository_analysis_runs (
					id,
					repository_connection_id,
					run_number,
					workflow_path,
					workflow_content_hash,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analysis_schema_version,
					analyzer_model_version,
					analysis_json
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				repositoryConnectionId,
				nextRunNumber,
				workflowPath,
				sha256(workflowContent),
				analysis.provider().value(),
				analysis.overallScore(),
				analysis.overallLevel(),
				analysis.overallStatus().value(),
				ANALYSIS_SCHEMA_VERSION,
				ANALYZER_MODEL_VERSION,
				analysisJson(analysis));
		return findById(id).orElseThrow();
	}

	private Optional<PersistedRepositoryAnalysis> findById(UUID id) {
		return jdbcTemplate.query("""
				SELECT id,
					repository_connection_id,
					run_number,
					workflow_path,
					workflow_content_hash,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version,
					analysis_json
				FROM repository_analysis_runs
				WHERE id = ?
				""", this::mapPersistedAnalysis, id).stream().findFirst();
	}

	private PersistedRepositoryAnalysis mapPersistedAnalysis(ResultSet rs, int rowNum) throws SQLException {
		return new PersistedRepositoryAnalysis(
				mapSummary(rs, rowNum),
				analysisResponse(rs.getString("analysis_json")));
	}

	private RepositoryAnalysisSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryAnalysisSummary(
				rs.getObject("id", UUID.class),
				rs.getObject("repository_connection_id", UUID.class),
				rs.getInt("run_number"),
				rs.getString("workflow_path"),
				rs.getString("workflow_content_hash"),
				rs.getString("provider"),
				rs.getDouble("overall_score"),
				rs.getInt("overall_level"),
				rs.getString("overall_status"),
				rs.getObject("analyzed_at", OffsetDateTime.class),
				rs.getInt("analysis_schema_version"),
				rs.getString("analyzer_model_version"));
	}

	private String analysisJson(AnalysisResponse analysis) {
		try {
			return objectMapper.writeValueAsString(analysis);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Repository analysis could not be serialized.", ex);
		}
	}

	private AnalysisResponse analysisResponse(String json) {
		try {
			return objectMapper.readValue(json, AnalysisResponse.class);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Repository analysis could not be read.", ex);
		}
	}

	private String sha256(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available.", ex);
		}
	}
}
