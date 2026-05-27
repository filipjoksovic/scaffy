package com.scaffy.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RepositoryAnalysisMigrationTest {

	@Test
	void migratesExistingSingleAnalysisIntoFirstRun() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:analysis-migration-" + UUID.randomUUID()
						+ ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
				"sa",
				"");
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.target("4")
				.load()
				.migrate();

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID userId = UUID.randomUUID();
		UUID repositoryId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO users (id, email, display_name, avatar_url)
				VALUES (?, ?, ?, ?)
				""", userId, "dev@example.com", "Dev User", "https://example.com/avatar.png");
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
				""", repositoryId, userId, "github", "scaffy-labs", "demo-app", "https://github.com/scaffy-labs/demo-app");
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
				repositoryId,
				".github/workflows/ci.yml",
				"github-actions",
				0.42,
				2,
				"partial",
				1,
				"capability-analyzer-v1",
				"""
						{"provider":"github-actions","overallScore":0.42,"overallLevel":2,"overallStatus":"partial","dimensions":[]}
						""");

		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load()
				.migrate();

		Integer runCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM repository_analysis_runs WHERE repository_connection_id = ?",
				Integer.class,
				repositoryId);
		Integer runNumber = jdbcTemplate.queryForObject(
				"SELECT run_number FROM repository_analysis_runs WHERE repository_connection_id = ?",
				Integer.class,
				repositoryId);
		Integer oldTable = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'repository_analyses'",
				Integer.class);

		assertThat(runCount).isOne();
		assertThat(runNumber).isOne();
		assertThat(oldTable).isZero();
	}
}
