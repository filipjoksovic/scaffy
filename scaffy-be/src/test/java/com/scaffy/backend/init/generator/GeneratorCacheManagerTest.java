package com.scaffy.backend.init.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratorCacheManagerTest {

	private GeneratorCacheManager cache;

	@BeforeEach
	void setUp() {
		cache = new GeneratorCacheManager();
	}

	// ------------------------------------------------------------------
	// Artifact cache
	// ------------------------------------------------------------------

	@Test
	void artifactMissReturnsNull() {
		assertThat(cache.getArtifact("artifacts/spring-boot.zip")).isNull();
	}

	@Test
	void artifactPutThenGetReturnsBytes() {
		byte[] bytes = { 1, 2, 3 };
		cache.putArtifact("artifacts/spring-boot.zip", bytes);
		assertThat(cache.getArtifact("artifacts/spring-boot.zip")).isEqualTo(bytes);
	}

	@Test
	void artifactPutNullIsIgnored() {
		cache.putArtifact("artifacts/spring-boot.zip", null);
		assertThat(cache.getArtifact("artifacts/spring-boot.zip")).isNull();
	}

	@Test
	void artifactEvictRemovesEntry() {
		cache.putArtifact("artifacts/angular.zip", new byte[] { 7 });
		cache.evictArtifact("artifacts/angular.zip");
		assertThat(cache.getArtifact("artifacts/angular.zip")).isNull();
	}

	@Test
	void artifactEvictUnknownKeyIsNoop() {
		// should not throw
		cache.evictArtifact("artifacts/nonexistent.zip");
	}

	// ------------------------------------------------------------------
	// Template cache
	// ------------------------------------------------------------------

	@Test
	void templateMissReturnsNull() {
		assertThat(cache.getTemplate("templates/root/README.md.tmpl")).isNull();
	}

	@Test
	void templatePutThenGetReturnsBytes() {
		byte[] bytes = "# Hello".getBytes();
		cache.putTemplate("templates/root/README.md.tmpl", bytes);
		assertThat(cache.getTemplate("templates/root/README.md.tmpl")).isEqualTo(bytes);
	}

	@Test
	void templatePutNullIsIgnored() {
		cache.putTemplate("templates/root/README.md.tmpl", null);
		assertThat(cache.getTemplate("templates/root/README.md.tmpl")).isNull();
	}

	@Test
	void templateEvictRemovesEntry() {
		cache.putTemplate("templates/root/gitignore", new byte[] { 0 });
		cache.evictTemplate("templates/root/gitignore");
		assertThat(cache.getTemplate("templates/root/gitignore")).isNull();
	}

	@Test
	void templateEvictUnknownKeyIsNoop() {
		cache.evictTemplate("templates/nonexistent.tmpl");
	}

	// ------------------------------------------------------------------
	// clearAll
	// ------------------------------------------------------------------

	@Test
	void clearAllRemovesBothCaches() {
		cache.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cache.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });

		cache.clearAll();

		assertThat(cache.getArtifact("artifacts/spring-boot.zip")).isNull();
		assertThat(cache.getTemplate("templates/root/README.md.tmpl")).isNull();
	}

	@Test
	void clearAllOnEmptyCacheIsNoop() {
		cache.clearAll(); // must not throw
		assertThat(cache.stats().artifactEntries()).isZero();
		assertThat(cache.stats().templateEntries()).isZero();
	}

	// ------------------------------------------------------------------
	// Stats
	// ------------------------------------------------------------------

	@Test
	void statsReflectsEmptyCache() {
		GeneratorCacheManager.CacheStats stats = cache.stats();
		assertThat(stats.artifactEntries()).isZero();
		assertThat(stats.templateEntries()).isZero();
		assertThat(stats.artifactHits()).isZero();
		assertThat(stats.artifactMisses()).isZero();
		assertThat(stats.templateHits()).isZero();
		assertThat(stats.templateMisses()).isZero();
		assertThat(stats.artifactKeys()).isEmpty();
		assertThat(stats.templateKeys()).isEmpty();
	}

	@Test
	void statsCountsHitsAndMisses() {
		cache.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cache.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });

		cache.getArtifact("artifacts/spring-boot.zip");  // hit
		cache.getArtifact("artifacts/angular.zip");      // miss
		cache.getTemplate("templates/root/README.md.tmpl"); // hit
		cache.getTemplate("templates/root/gitignore");      // miss

		GeneratorCacheManager.CacheStats stats = cache.stats();
		assertThat(stats.artifactHits()).isEqualTo(1);
		assertThat(stats.artifactMisses()).isEqualTo(1);
		assertThat(stats.templateHits()).isEqualTo(1);
		assertThat(stats.templateMisses()).isEqualTo(1);
	}

	@Test
	void statsReflectsCurrentEntryCounts() {
		cache.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cache.putArtifact("artifacts/angular.zip", new byte[] { 2 });
		cache.putTemplate("templates/root/README.md.tmpl", new byte[] { 3 });

		GeneratorCacheManager.CacheStats stats = cache.stats();
		assertThat(stats.artifactEntries()).isEqualTo(2);
		assertThat(stats.templateEntries()).isEqualTo(1);
	}

	@Test
	void statsKeyListIsSorted() {
		cache.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cache.putArtifact("artifacts/angular.zip", new byte[] { 2 });
		cache.putArtifact("artifacts/nestjs.zip", new byte[] { 3 });

		assertThat(cache.stats().artifactKeys())
				.isSortedAccordingTo(String::compareTo)
				.containsExactly(
						"artifacts/angular.zip",
						"artifacts/nestjs.zip",
						"artifacts/spring-boot.zip");
	}

	@Test
	void statsDropsToZeroAfterClearAll() {
		cache.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cache.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });
		cache.clearAll();

		GeneratorCacheManager.CacheStats stats = cache.stats();
		assertThat(stats.artifactEntries()).isZero();
		assertThat(stats.templateEntries()).isZero();
		assertThat(stats.artifactKeys()).isEmpty();
		assertThat(stats.templateKeys()).isEmpty();
	}

	// ------------------------------------------------------------------
	// View helpers (used by tests in ArtifactComposerTest)
	// ------------------------------------------------------------------

	@Test
	void artifactCacheViewReflectsContents() {
		cache.putArtifact("artifacts/vue.zip", new byte[] { 9 });
		assertThat(cache.artifactCacheView()).containsKey("artifacts/vue.zip");
	}

	@Test
	void templateCacheViewReflectsContents() {
		cache.putTemplate("templates/docker/docker-compose.yml.tmpl", new byte[] { 5 });
		assertThat(cache.templateCacheView()).containsKey("templates/docker/docker-compose.yml.tmpl");
	}
}
