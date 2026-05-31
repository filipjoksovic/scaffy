package com.scaffy.backend.init.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that {@link TemplateOverlay#emit} populates and reuses the
 * {@link GeneratorCacheManager} template cache.
 */
@SpringBootTest
class TemplateOverlayCacheTest {

	@Autowired
	private TemplateOverlay templateOverlay;

	@Autowired
	private GeneratorCacheManager cacheManager;

	private static final Map<String, String> VARS = Map.of(
			"projectName", "cache-test",
			"projectPascal", "CacheTest",
			"frontendLabel", "Angular application",
			"frontendDevCmd", "npm start",
			"frontendPort", "4200",
			"frontendDistPath", "dist/cache-test/browser",
			"backendLabel", "Spring Boot service",
			"backendRunCmd", "mvn spring-boot:run",
			"backendPort", "8080");

	@BeforeEach
	void clearCache() {
		cacheManager.clearAll();
	}

	@Test
	void firstEmitPopulatesTemplateCache() throws IOException {
		List<TemplateFile> templates = List.of(
				TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"),
				TemplateFile.copy("templates/root/gitignore", ".gitignore"));

		assertThat(cacheManager.getTemplate("templates/root/README.md.tmpl")).isNull();
		assertThat(cacheManager.getTemplate("templates/root/gitignore")).isNull();

		templateOverlay.emit(templates, VARS);

		assertThat(cacheManager.getTemplate("templates/root/README.md.tmpl")).isNotNull();
		assertThat(cacheManager.getTemplate("templates/root/gitignore")).isNotNull();
	}

	@Test
	void secondEmitIsServedFromCache() throws IOException {
		List<TemplateFile> templates = List.of(
				TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"));

		templateOverlay.emit(templates, VARS); // cold

		long missesBefore = cacheManager.stats().templateMisses();
		long hitsBefore   = cacheManager.stats().templateHits();

		templateOverlay.emit(templates, VARS); // warm

		assertThat(cacheManager.stats().templateMisses()).isEqualTo(missesBefore);
		assertThat(cacheManager.stats().templateHits()).isEqualTo(hitsBefore + 1);
	}

	@Test
	void cachedBytesProduceSameOutputAsFreshLoad() throws IOException {
		List<TemplateFile> templates = List.of(
				TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"),
				TemplateFile.copy("templates/root/gitignore", ".gitignore"));

		List<EmittedFile> first  = templateOverlay.emit(templates, VARS);

		cacheManager.evictTemplate("templates/root/README.md.tmpl");
		cacheManager.evictTemplate("templates/root/gitignore");

		List<EmittedFile> second = templateOverlay.emit(templates, VARS);

		assertThat(first).hasSameSizeAs(second);
		for (int i = 0; i < first.size(); i++) {
			assertThat(first.get(i).destinationPath()).isEqualTo(second.get(i).destinationPath());
			assertThat(first.get(i).content()).isEqualTo(second.get(i).content());
		}
	}

	@Test
	void nonRenderedTemplateIsCachedUnchanged() throws IOException {
		List<TemplateFile> templates = List.of(
				TemplateFile.copy("templates/root/gitignore", ".gitignore"));

		templateOverlay.emit(templates, VARS);

		byte[] cached = cacheManager.getTemplate("templates/root/gitignore");
		assertThat(cached).isNotNull().isNotEmpty();
		// .gitignore is a copy (not rendered) so the raw bytes must not contain {{
		assertThat(new String(cached)).doesNotContain("{{");
	}

	@Test
	void emitThrowsForMissingTemplate() {
		List<TemplateFile> templates = List.of(
				TemplateFile.copy("templates/nonexistent.tmpl", "out.txt"));

		assertThatThrownBy(() -> templateOverlay.emit(templates, VARS))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("Template resource not found");
	}

	@Test
	void evictThenEmitAgainRepopulatesCache() throws IOException {
		List<TemplateFile> templates = List.of(
				TemplateFile.rendered("templates/root/README.md.tmpl", "README.md"));

		templateOverlay.emit(templates, VARS);
		cacheManager.evictTemplate("templates/root/README.md.tmpl");
		assertThat(cacheManager.getTemplate("templates/root/README.md.tmpl")).isNull();

		templateOverlay.emit(templates, VARS);
		assertThat(cacheManager.getTemplate("templates/root/README.md.tmpl")).isNotNull();
	}
}
