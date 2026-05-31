package com.scaffy.backend.init.favourite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FavouriteStackRepository {

	private static final int MAX_PER_USER = 20;

	private final JdbcTemplate jdbc;

	public FavouriteStackRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<FavouriteStack> findByUserId(UUID userId) {
		return jdbc.query("""
				SELECT id, user_id, name,
				       frontend, frontend_version, frontend_runtime,
				       backend, backend_version, backend_runtime,
				       pipeline, pipeline_maturity, include_docker, created_at
				FROM favourite_stacks
				WHERE user_id = ?
				ORDER BY created_at DESC
				LIMIT ?
				""", this::map, userId, MAX_PER_USER);
	}

	public Optional<FavouriteStack> findByIdAndUserId(UUID id, UUID userId) {
		return jdbc.query("""
				SELECT id, user_id, name,
				       frontend, frontend_version, frontend_runtime,
				       backend, backend_version, backend_runtime,
				       pipeline, pipeline_maturity, include_docker, created_at
				FROM favourite_stacks
				WHERE id = ? AND user_id = ?
				""", this::map, id, userId).stream().findFirst();
	}

	public int countByUserId(UUID userId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM favourite_stacks WHERE user_id = ?",
				Integer.class, userId);
		return count == null ? 0 : count;
	}

	public FavouriteStack save(FavouriteStack favourite) {
		jdbc.update("""
				INSERT INTO favourite_stacks
				    (id, user_id, name,
				     frontend, frontend_version, frontend_runtime,
				     backend, backend_version, backend_runtime,
				     pipeline, pipeline_maturity, include_docker, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				favourite.id(),
				favourite.userId(),
				favourite.name(),
				favourite.frontend(),
				favourite.frontendVersion(),
				favourite.frontendRuntime(),
				favourite.backend(),
				favourite.backendVersion(),
				favourite.backendRuntime(),
				favourite.pipeline(),
				favourite.pipelineMaturity(),
				favourite.includeDocker(),
				favourite.createdAt());
		return favourite;
	}

	public boolean deleteByIdAndUserId(UUID id, UUID userId) {
		return jdbc.update("""
				DELETE FROM favourite_stacks
				WHERE id = ? AND user_id = ?
				""", id, userId) > 0;
	}

	public int maxPerUser() {
		return MAX_PER_USER;
	}

	private FavouriteStack map(ResultSet rs, int rowNum) throws SQLException {
		return new FavouriteStack(
				rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class),
				rs.getString("name"),
				rs.getString("frontend"),
				rs.getString("frontend_version"),
				rs.getString("frontend_runtime"),
				rs.getString("backend"),
				rs.getString("backend_version"),
				rs.getString("backend_runtime"),
				rs.getString("pipeline"),
				rs.getString("pipeline_maturity"),
				rs.getBoolean("include_docker"),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
