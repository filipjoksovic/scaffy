package com.scaffy.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AuthSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesAuthTables() {
		Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		Integer oauthAccountCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth_accounts", Integer.class);
		Integer repositoryConnectionCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM repository_connections",
				Integer.class);

		assertThat(userCount).isNotNull();
		assertThat(oauthAccountCount).isNotNull();
		assertThat(repositoryConnectionCount).isNotNull();
	}
}
