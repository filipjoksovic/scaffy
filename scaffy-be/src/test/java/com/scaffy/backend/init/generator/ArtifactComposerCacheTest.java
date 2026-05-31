package com.scaffy.backend.init.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that {@link ArtifactComposer#compose} populates and reuses the
 * {@link GeneratorCacheManager} artifact cache.
 */
@SpringBootTest
class ArtifactComposerCacheTest {

	@Autowired
	private ArtifactComposer artifactComposer;

	@Autowired
	private GeneratorCacheManager cacheManager;

	private static final Map<String, String> TOKENS = Map.of(
			"__SCAFFY_PROJECT_NAME__", "cache-test",
			"__SCAFFY_PROJECT_PASCAL__", "CacheTest",
			"__SCAFFY_PROJECT_CAMEL__", "cacheTest",
			"__SCAFFY_PACKAGE__", "com.example.cachetest",
			"__SCAFFY_PACKAGE_DIR__", "com/example/cachetest");

	@BeforeEach
	void clearCache() {
		cacheManager.clearAll();
	}

	@Test
	void firstComposePopulatesCache() throws IOException {
		assertThat(cacheManager.getArtifact("artifacts/spring-boot.zip")).isNull();

		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);

		assertThat(cacheManager.getArtifact("artifacts/spring-boot.zip")).isNotNull();
	}

	@Test
	void secondComposeIsServedFromCache() throws IOException {
		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);

		long missesBefore = cacheManager.stats().artifactMisses();
		long hitsBefore   = cacheManager.stats().artifactHits();

		// Second call — should be a cache hit.
		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);

		assertThat(cacheManager.stats().artifactMisses()).isEqualTo(missesBefore);
		assertThat(cacheManager.stats().artifactHits()).isEqualTo(hitsBefore + 1);
	}

	@Test
	void cachedBytesProduceSameOutputAsFreshLoad() throws IOException {
		var first  = artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);

		cacheManager.evictArtifact("artifacts/spring-boot.zip");

		var second = artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);

		assertThat(first).hasSameSizeAs(second);
		for (int i = 0; i < first.size(); i++) {
			assertThat(first.get(i).destinationPath()).isEqualTo(second.get(i).destinationPath());
			assertThat(first.get(i).content()).isEqualTo(second.get(i).content());
		}
	}

	@Test
	void differentArtifactsCachedUnderSeparateKeys() throws IOException {
		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);
		artifactComposer.compose("artifacts/angular.zip", "frontend", TOKENS);

		assertThat(cacheManager.artifactCacheView())
				.containsKeys("artifacts/spring-boot.zip", "artifacts/angular.zip");
	}

	@Test
	void composeThrowsForMissingArtifact() {
		assertThatThrownBy(() -> artifactComposer.compose("artifacts/nonexistent.zip", "backend", TOKENS))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("Artifact not found on classpath");
	}

	@Test
	void evictThenRecomposeRepopulatesCache() throws IOException {
		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);
		cacheManager.evictArtifact("artifacts/spring-boot.zip");
		assertThat(cacheManager.getArtifact("artifacts/spring-boot.zip")).isNull();

		artifactComposer.compose("artifacts/spring-boot.zip", "backend", TOKENS);
		assertThat(cacheManager.getArtifact("artifacts/spring-boot.zip")).isNotNull();
	}
}
