package com.scaffy.backend.analyze;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AnalyzeSamplePipelinesIntegrationTest {

	private static final Path SAMPLE_DIR = Path.of("src/test/resources/analyze-samples");

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void samplePipelineFilesCanBeUploadedThroughAnalyzeEndpoint() throws Exception {
		for (Sample sample : samples()) {
			mockMvc().perform(multipart("/api/analyze").file(file(sample.filename())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.provider").value(sample.provider()))
					.andExpect(jsonPath("$.dimensions[0].dimension").value("build"))
					.andExpect(jsonPath("$.dimensions[0].score").value(sample.buildScore()))
					.andExpect(jsonPath("$.dimensions[0].level").value(sample.buildLevel()))
					.andExpect(jsonPath("$.dimensions[0].status").value(sample.buildStatus()))
					.andExpect(jsonPath("$.dimensions[0].confidence").value(sample.buildConfidence()))
					.andExpect(jsonPath("$.dimensions[1].dimension").value("test"))
					.andExpect(jsonPath("$.dimensions[1].score").value(sample.testScore()))
					.andExpect(jsonPath("$.dimensions[1].level").value(sample.testLevel()))
					.andExpect(jsonPath("$.dimensions[1].status").value(sample.testStatus()))
					.andExpect(jsonPath("$.dimensions[1].confidence").value(sample.testConfidence()));
		}
	}

	private List<Sample> samples() {
		return List.of(
				new Sample("github-01-test-only-missing.yml", "github-actions", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium"),
				new Sample("github-02-manual-build-low.yml", "github-actions", 0.5, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				new Sample("github-03-node-build-no-artifact.yml", "github-actions", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("github-04-node-build-with-artifact.yml", "github-actions", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("gitlab-05-docker-build-no-explicit-deps.yml", "gitlab-ci", 0.8, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("gitlab-06-manual-java-build.yml", "gitlab-ci", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high"),
				new Sample("gitlab-07-java-build-complete.yml", "gitlab-ci", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("gitlab-08-dotnet-build-complete.yml", "gitlab-ci", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("test-01-build-only-test-missing.yml", "github-actions", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high"),
				new Sample("test-02-manual-test-weak.yml", "github-actions", 0.0, 1, "missing", "high", 0.5, 3, "partial", "medium"),
				new Sample("test-03-automated-test-partial.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium"),
				new Sample("test-04-test-with-artifact-strong.yml", "github-actions", 0.0, 1, "missing", "high", 0.85, 5, "complete", "high"),
				new Sample("test-05-complete-test-suite.yml", "gitlab-ci", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high"));
	}

	private MockMultipartFile file(String filename) throws IOException {
		return new MockMultipartFile(
				"file",
				filename,
				"application/x-yaml",
				Files.readAllBytes(SAMPLE_DIR.resolve(filename)));
	}

	private record Sample(
			String filename,
			String provider,
			double buildScore,
			int buildLevel,
			String buildStatus,
			String buildConfidence,
			double testScore,
			int testLevel,
			String testStatus,
			String testConfidence) {
	}
}
