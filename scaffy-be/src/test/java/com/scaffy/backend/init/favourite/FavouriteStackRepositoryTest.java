package com.scaffy.backend.init.favourite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FavouriteStackRepositoryTest {

	@Autowired
	private FavouriteStackRepository repository;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;
	private UUID otherUserId;

	@BeforeEach
	void setUp() {
		userId      = UUID.randomUUID();
		otherUserId = UUID.randomUUID();
		insertUser(userId,      "user@example.com",  "User One");
		insertUser(otherUserId, "other@example.com", "User Two");
		jdbc.update("DELETE FROM favourite_stacks WHERE user_id IN (?, ?)", userId, otherUserId);
	}

	// ------------------------------------------------------------------
	// save + findByUserId
	// ------------------------------------------------------------------

	@Test
	void savedFavouriteIsReturnedByFindByUserId() {
		FavouriteStack fav = build(userId, "My Stack");
		repository.save(fav);

		List<FavouriteStack> found = repository.findByUserId(userId);
		assertThat(found).hasSize(1);
		FavouriteStack result = found.get(0);
		assertThat(result.id()).isEqualTo(fav.id());
		assertThat(result.userId()).isEqualTo(userId);
		assertThat(result.name()).isEqualTo("My Stack");
		assertThat(result.frontend()).isEqualTo("react");
		assertThat(result.frontendVersion()).isEqualTo("19");
		assertThat(result.frontendRuntime()).isEqualTo("node-22");
		assertThat(result.backend()).isEqualTo("spring-boot");
		assertThat(result.backendVersion()).isEqualTo("4.0");
		assertThat(result.backendRuntime()).isEqualTo("java-21");
		assertThat(result.pipeline()).isEqualTo("github-actions");
		assertThat(result.pipelineMaturity()).isEqualTo("l2");
		assertThat(result.includeDocker()).isTrue();
	}

	@Test
	void findByUserIdReturnsOnlyOwnEntries() {
		repository.save(build(userId,      "Mine"));
		repository.save(build(otherUserId, "Theirs"));

		assertThat(repository.findByUserId(userId)).hasSize(1)
				.extracting(FavouriteStack::name).containsExactly("Mine");
		assertThat(repository.findByUserId(otherUserId)).hasSize(1)
				.extracting(FavouriteStack::name).containsExactly("Theirs");
	}

	@Test
	void findByUserIdReturnsNewestFirst() throws InterruptedException {
		repository.save(build(userId, "First",  OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2)));
		repository.save(build(userId, "Second", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
		repository.save(build(userId, "Third",  OffsetDateTime.now(ZoneOffset.UTC)));

		List<String> names = repository.findByUserId(userId).stream()
				.map(FavouriteStack::name).toList();
		assertThat(names).containsExactly("Third", "Second", "First");
	}

	@Test
	void findByUserIdReturnsEmptyListWhenNoneExist() {
		assertThat(repository.findByUserId(userId)).isEmpty();
	}

	@Test
	void findByUserIdIsLimitedToMaxPerUser() {
		int max = repository.maxPerUser();
		for (int i = 0; i < max + 5; i++) {
			jdbc.update("""
					INSERT INTO favourite_stacks
					    (id, user_id, name, frontend, frontend_version, frontend_runtime,
					     backend, backend_version, backend_runtime,
					     pipeline, pipeline_maturity, include_docker, created_at)
					VALUES (?, ?, ?, 'react', '19', 'node-22', 'spring-boot', '4.0', 'java-21',
					        'github-actions', 'l2', false, ?)
					""",
					UUID.randomUUID(), userId, "Stack " + i,
					OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(i));
		}
		assertThat(repository.findByUserId(userId)).hasSize(max);
	}

	// ------------------------------------------------------------------
	// findByIdAndUserId
	// ------------------------------------------------------------------

	@Test
	void findByIdAndUserIdReturnsPresentForOwner() {
		FavouriteStack fav = build(userId, "My Stack");
		repository.save(fav);

		Optional<FavouriteStack> found = repository.findByIdAndUserId(fav.id(), userId);
		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(fav.id());
	}

	@Test
	void findByIdAndUserIdReturnsEmptyForWrongUser() {
		FavouriteStack fav = build(userId, "My Stack");
		repository.save(fav);

		assertThat(repository.findByIdAndUserId(fav.id(), otherUserId)).isEmpty();
	}

	@Test
	void findByIdAndUserIdReturnsEmptyForUnknownId() {
		assertThat(repository.findByIdAndUserId(UUID.randomUUID(), userId)).isEmpty();
	}

	// ------------------------------------------------------------------
	// countByUserId
	// ------------------------------------------------------------------

	@Test
	void countByUserIdReturnsZeroInitially() {
		assertThat(repository.countByUserId(userId)).isZero();
	}

	@Test
	void countByUserIdReflectsSavedEntries() {
		repository.save(build(userId, "A"));
		repository.save(build(userId, "B"));
		assertThat(repository.countByUserId(userId)).isEqualTo(2);
	}

	@Test
	void countByUserIdDoesNotCountOtherUsersEntries() {
		repository.save(build(otherUserId, "Theirs"));
		assertThat(repository.countByUserId(userId)).isZero();
	}

	// ------------------------------------------------------------------
	// deleteByIdAndUserId
	// ------------------------------------------------------------------

	@Test
	void deleteByIdAndUserIdReturnsTrueAndRemovesEntry() {
		FavouriteStack fav = build(userId, "To Delete");
		repository.save(fav);

		boolean deleted = repository.deleteByIdAndUserId(fav.id(), userId);
		assertThat(deleted).isTrue();
		assertThat(repository.findByUserId(userId)).isEmpty();
	}

	@Test
	void deleteByIdAndUserIdReturnsFalseForWrongUser() {
		FavouriteStack fav = build(userId, "Mine");
		repository.save(fav);

		boolean deleted = repository.deleteByIdAndUserId(fav.id(), otherUserId);
		assertThat(deleted).isFalse();
		assertThat(repository.findByUserId(userId)).hasSize(1);
	}

	@Test
	void deleteByIdAndUserIdReturnsFalseForUnknownId() {
		assertThat(repository.deleteByIdAndUserId(UUID.randomUUID(), userId)).isFalse();
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private FavouriteStack build(UUID owner, String name) {
		return build(owner, name, OffsetDateTime.now(ZoneOffset.UTC));
	}

	private FavouriteStack build(UUID owner, String name, OffsetDateTime createdAt) {
		return new FavouriteStack(
				UUID.randomUUID(), owner, name,
				"react", "19", "node-22",
				"spring-boot", "4.0", "java-21",
				"github-actions", "l2", true, createdAt);
	}

	private void insertUser(UUID id, String email, String displayName) {
		Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
		if (existing == null || existing == 0) {
			jdbc.update("INSERT INTO users (id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
		}
	}
}
