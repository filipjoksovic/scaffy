package com.scaffy.backend.init.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CacheControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private GeneratorCacheManager cacheManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		cacheManager.clearAll();
	}

	// ------------------------------------------------------------------
	// GET /api/cache/stats
	// ------------------------------------------------------------------

	@Test
	void statsReturnsOkWithZeroEntriesOnEmptyCache() throws Exception {
		// Hit/miss counters are cumulative for the JVM lifetime and not reset by
		// clearAll(), so we only assert on the entry counts here.
		mockMvc.perform(get("/api/cache/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.artifactEntries").value(0))
				.andExpect(jsonPath("$.templateEntries").value(0));
	}

	@Test
	void statsReflectsPopulatedCache() throws Exception {
		cacheManager.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cacheManager.putArtifact("artifacts/angular.zip", new byte[] { 2 });
		cacheManager.putTemplate("templates/root/README.md.tmpl", new byte[] { 3 });

		mockMvc.perform(get("/api/cache/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.artifactEntries").value(2))
				.andExpect(jsonPath("$.templateEntries").value(1));
	}

	// ------------------------------------------------------------------
	// DELETE /api/cache  (clear all)
	// ------------------------------------------------------------------

	@Test
	void deleteWithNoParamsClearsEntireCache() throws Exception {
		cacheManager.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cacheManager.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });

		mockMvc.perform(delete("/api/cache"))
				.andExpect(status().isOk());

		assertThat(cacheManager.stats().artifactEntries()).isZero();
		assertThat(cacheManager.stats().templateEntries()).isZero();
	}

	@Test
	void deleteWithNoParamsOnEmptyCacheReturnsOk() throws Exception {
		mockMvc.perform(delete("/api/cache"))
				.andExpect(status().isOk());
	}

	// ------------------------------------------------------------------
	// DELETE /api/cache?artifact=...
	// ------------------------------------------------------------------

	@Test
	void deleteWithArtifactParamEvictsOnlyThatEntry() throws Exception {
		cacheManager.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cacheManager.putArtifact("artifacts/angular.zip", new byte[] { 2 });

		mockMvc.perform(delete("/api/cache").param("artifact", "artifacts/spring-boot.zip"))
				.andExpect(status().isOk());

		assertThat(cacheManager.getArtifact("artifacts/spring-boot.zip")).isNull();
		assertThat(cacheManager.getArtifact("artifacts/angular.zip")).isNotNull();
	}

	@Test
	void deleteWithArtifactParamLeavesTemplatesUntouched() throws Exception {
		cacheManager.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cacheManager.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });

		mockMvc.perform(delete("/api/cache").param("artifact", "artifacts/spring-boot.zip"))
				.andExpect(status().isOk());

		assertThat(cacheManager.stats().templateEntries()).isEqualTo(1);
	}

	// ------------------------------------------------------------------
	// DELETE /api/cache?template=...
	// ------------------------------------------------------------------

	@Test
	void deleteWithTemplateParamEvictsOnlyThatEntry() throws Exception {
		cacheManager.putTemplate("templates/root/README.md.tmpl", new byte[] { 1 });
		cacheManager.putTemplate("templates/root/gitignore", new byte[] { 2 });

		mockMvc.perform(delete("/api/cache").param("template", "templates/root/README.md.tmpl"))
				.andExpect(status().isOk());

		assertThat(cacheManager.getTemplate("templates/root/README.md.tmpl")).isNull();
		assertThat(cacheManager.getTemplate("templates/root/gitignore")).isNotNull();
	}

	@Test
	void deleteWithTemplateParamLeavesArtifactsUntouched() throws Exception {
		cacheManager.putArtifact("artifacts/spring-boot.zip", new byte[] { 1 });
		cacheManager.putTemplate("templates/root/README.md.tmpl", new byte[] { 2 });

		mockMvc.perform(delete("/api/cache").param("template", "templates/root/README.md.tmpl"))
				.andExpect(status().isOk());

		assertThat(cacheManager.stats().artifactEntries()).isEqualTo(1);
	}
}
