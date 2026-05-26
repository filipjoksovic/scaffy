package com.scaffy.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
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

	public Optional<PersistedRepositoryAnalysis> findByRepositoryConnectionId(UUID repositoryConnectionId) {
		return jdbcTemplate.query("""
				SELECT repository_connection_id,
					workflow_path,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version,
					analysis_json
				FROM repository_analyses
				WHERE repository_connection_id = ?
				""", this::mapPersistedAnalysis, repositoryConnectionId).stream().findFirst();
	}

	public Map<UUID, RepositoryAnalysisSummary> findSummariesByRepositoryConnectionIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
		List<RepositoryAnalysisSummary> summaries = jdbcTemplate.query("""
				SELECT repository_connection_id,
					workflow_path,
					provider,
					overall_score,
					overall_level,
					overall_status,
					analyzed_at,
					analysis_schema_version,
					analyzer_model_version
				FROM repository_analyses
				WHERE repository_connection_id IN (%s)
				""".formatted(placeholders), this::mapSummary, ids.toArray());
		return summaries.stream().collect(Collectors.toMap(RepositoryAnalysisSummary::repositoryConnectionId, summary -> summary));
	}

	public PersistedRepositoryAnalysis insert(UUID repositoryConnectionId, String workflowPath, AnalysisResponse analysis) {
		try {
			jdbcTemplate.update("""
					INSERT INTO repository_analyses (
						repository_connection_id,
						workflow_path,
						provider,
						overall_score,
						overall_level,
						overall_status,
						analysis_schema_version,
						analyzer_model_version,
						analysis_json
					)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					repositoryConnectionId,
					workflowPath,
					analysis.provider().value(),
					analysis.overallScore(),
					analysis.overallLevel(),
					analysis.overallStatus().value(),
					ANALYSIS_SCHEMA_VERSION,
					ANALYZER_MODEL_VERSION,
					analysisJson(analysis));
		}
		catch (DuplicateKeyException ex) {
			return findByRepositoryConnectionId(repositoryConnectionId).orElseThrow();
		}
		return findByRepositoryConnectionId(repositoryConnectionId).orElseThrow();
	}

	private PersistedRepositoryAnalysis mapPersistedAnalysis(ResultSet rs, int rowNum) throws SQLException {
		return new PersistedRepositoryAnalysis(
				mapSummary(rs, rowNum),
				analysisResponse(rs.getString("analysis_json")));
	}

	private RepositoryAnalysisSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
		return new RepositoryAnalysisSummary(
				rs.getObject("repository_connection_id", UUID.class),
				rs.getString("workflow_path"),
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
}
