package com.scaffy.backend.recommend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FindingFixRepository {

	private final JdbcTemplate jdbcTemplate;

	public FindingFixRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<FindingFixResponse> find(UUID analysisRunId, String findingHash) {
		return jdbcTemplate.query("""
				SELECT model, summary, explanation, language, suggested_code,
					edit_mode, edit_after_line, edit_start_line, edit_end_line, edit_code
				FROM finding_fixes
				WHERE analysis_run_id = ? AND finding_hash = ?
				""", this::map, analysisRunId, findingHash).stream().findFirst();
	}

	public void save(UUID analysisRunId, String findingHash, FindingFixRequest.Finding finding,
			FindingFixResponse response) {
		FindingFixEdit edit = response.edit();
		jdbcTemplate.update("""
				INSERT INTO finding_fixes (
					id,
					analysis_run_id,
					finding_hash,
					rule_id,
					dimension,
					capability,
					finding_type,
					status,
					model,
					summary,
					explanation,
					language,
					suggested_code,
					edit_mode,
					edit_after_line,
					edit_start_line,
					edit_end_line,
					edit_code
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'ok', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (analysis_run_id, finding_hash) DO NOTHING
				""",
				UUID.randomUUID(),
				analysisRunId,
				findingHash,
				finding.ruleId(),
				finding.dimension(),
				finding.capability(),
				finding.type(),
				response.model(),
				response.summary(),
				response.explanation(),
				response.language(),
				response.suggestedCode(),
				edit == null ? null : edit.mode(),
				edit == null ? null : edit.afterLine(),
				edit == null ? null : edit.startLine(),
				edit == null ? null : edit.endLine(),
				edit == null ? null : edit.code());
	}

	private FindingFixResponse map(ResultSet rs, int rowNum) throws SQLException {
		return FindingFixResponse.ok(
				rs.getString("model"),
				rs.getString("summary"),
				rs.getString("explanation"),
				rs.getString("language"),
				rs.getString("suggested_code"),
				mapEdit(rs));
	}

	private FindingFixEdit mapEdit(ResultSet rs) throws SQLException {
		String mode = rs.getString("edit_mode");
		if (mode == null) {
			return null;
		}
		return new FindingFixEdit(
				mode,
				(Integer) rs.getObject("edit_after_line"),
				(Integer) rs.getObject("edit_start_line"),
				(Integer) rs.getObject("edit_end_line"),
				rs.getString("edit_code"));
	}
}
