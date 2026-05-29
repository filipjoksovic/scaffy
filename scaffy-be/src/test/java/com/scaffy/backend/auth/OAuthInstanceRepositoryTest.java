package com.scaffy.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OAuthInstanceRepositoryTest {

	@Autowired
	private OAuthInstanceRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void upsertRoundTripsAndEncryptsSecretAtRest() {
		OAuthInstance saved = repository.upsertByHost(
				"https://gitlab.roundtrip.test",
				"gitlab.roundtrip.test",
				"Round Trip",
				"client-id",
				"super-secret");

		assertThat(saved.registrationId()).isEqualTo("gitlab-gitlab-roundtrip-test");

		Optional<OAuthInstance> found = repository.findByRegistrationId("gitlab-gitlab-roundtrip-test");
		assertThat(found).isPresent();
		assertThat(found.get().clientSecret()).isEqualTo("super-secret");

		String storedSecret = jdbcTemplate.queryForObject(
				"SELECT client_secret_encrypted FROM oauth_instances WHERE host = ?",
				String.class,
				"gitlab.roundtrip.test");
		assertThat(storedSecret).isNotNull().isNotEqualTo("super-secret");
	}

	@Test
	void upsertByHostUpdatesExistingRow() {
		repository.upsertByHost("https://gitlab.update.test", "gitlab.update.test", "First", "id-1", "secret-1");
		repository.upsertByHost("https://gitlab.update.test", "gitlab.update.test", "Second", "id-2", "secret-2");

		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM oauth_instances WHERE host = ?", Integer.class, "gitlab.update.test");
		assertThat(rows).isEqualTo(1);

		OAuthInstance found = repository.findByRegistrationId("gitlab-gitlab-update-test").orElseThrow();
		assertThat(found.clientId()).isEqualTo("id-2");
		assertThat(found.clientSecret()).isEqualTo("secret-2");
		assertThat(found.displayName()).isEqualTo("Second");
	}

	@Test
	void listPublicExposesNoSecretField() {
		repository.upsertByHost("https://gitlab.public.test", "gitlab.public.test", "Public", "id", "secret");

		assertThat(repository.listPublic())
				.anySatisfy(summary -> assertThat(summary.registrationId()).isEqualTo("gitlab-gitlab-public-test"));
	}
}
