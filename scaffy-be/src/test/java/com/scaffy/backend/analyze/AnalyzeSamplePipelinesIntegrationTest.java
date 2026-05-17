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
					.andExpect(jsonPath("$.dimensions[1].confidence").value(sample.testConfidence()))
					.andExpect(jsonPath("$.dimensions[2].dimension").value("code_analysis"))
					.andExpect(jsonPath("$.dimensions[2].score").value(sample.codeAnalysisScore()))
					.andExpect(jsonPath("$.dimensions[2].level").value(sample.codeAnalysisLevel()))
					.andExpect(jsonPath("$.dimensions[2].status").value(sample.codeAnalysisStatus()))
					.andExpect(jsonPath("$.dimensions[2].confidence").value(sample.codeAnalysisConfidence()))
					.andExpect(jsonPath("$.dimensions[3].dimension").value("artifacts"))
					.andExpect(jsonPath("$.dimensions[3].score").value(sample.artifactScore()))
					.andExpect(jsonPath("$.dimensions[3].level").value(sample.artifactLevel()))
					.andExpect(jsonPath("$.dimensions[3].status").value(sample.artifactStatus()))
					.andExpect(jsonPath("$.dimensions[3].confidence").value(sample.artifactConfidence()))
					.andExpect(jsonPath("$.dimensions[4].dimension").value("deployment"))
					.andExpect(jsonPath("$.dimensions[4].score").value(sample.deploymentScore()))
					.andExpect(jsonPath("$.dimensions[4].level").value(sample.deploymentLevel()))
					.andExpect(jsonPath("$.dimensions[4].status").value(sample.deploymentStatus()))
					.andExpect(jsonPath("$.dimensions[4].confidence").value(sample.deploymentConfidence()))
					.andExpect(jsonPath("$.dimensions[5].dimension").value("notifications"))
					.andExpect(jsonPath("$.dimensions[5].score").value(sample.notificationScore()))
					.andExpect(jsonPath("$.dimensions[5].level").value(sample.notificationLevel()))
					.andExpect(jsonPath("$.dimensions[5].status").value(sample.notificationStatus()))
					.andExpect(jsonPath("$.dimensions[5].confidence").value(sample.notificationConfidence()));
		}
	}

	private List<Sample> samples() {
		return List.of(
				sample("github-01-test-only-missing.yml", "github-actions", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("github-02-manual-build-low.yml", "github-actions", 0.5, 3, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("github-03-node-build-no-artifact.yml", "github-actions", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("github-04-node-build-with-artifact.yml", "github-actions", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("gitlab-05-docker-build-no-explicit-deps.yml", "gitlab-ci", 0.8, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.6, 4, "partial", "medium", 0.0, 1, "missing", "high"),
				sample("gitlab-06-manual-java-build.yml", "gitlab-ci", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("gitlab-07-java-build-complete.yml", "gitlab-ci", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("gitlab-08-dotnet-build-complete.yml", "gitlab-ci", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sample("test-01-build-only-test-missing.yml", "github-actions", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("test-02-manual-test-weak.yml", "github-actions", 0.0, 1, "missing", "high", 0.5, 3, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("test-03-automated-test-partial.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("test-04-test-with-artifact-strong.yml", "github-actions", 0.0, 1, "missing", "high", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("test-05-complete-test-suite.yml", "gitlab-ci", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sample("deploy-01-build-test-only-missing.yml", "github-actions", 0.85, 5, "complete", "high", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("deploy-02-manual-deploy-partial.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium"),
				sample("deploy-03-auto-deploy-no-validation.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.85, 5, "complete", "medium"),
				sample("deploy-04-complete-kubernetes.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high"),
				sample("deploy-05-cloud-provider.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high"),
				sample("quality-01-build-only-missing.yml", "github-actions", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sample("quality-02-github-lint-partial.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("quality-03-github-typescript-complete.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("quality-04-gitlab-java-python.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sample("quality-05-sonar-super-linter.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.8, 5, "complete", "high", 0.0, 1, "missing", "high"),
				sample("artifact-01-missing.yml", "github-actions", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("artifact-02-github-upload-partial.yml", "github-actions", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("artifact-03-gitlab-paths-partial.yml", "gitlab-ci", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.45, 3, "partial", "medium", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("artifact-04-docker-image-complete.yml", "github-actions", 0.8, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high", 0.0, 1, "missing", "high"),
				sampleWithArtifacts("artifact-05-package-publish-complete.yml", "github-actions", 0.8, 5, "complete", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.85, 5, "complete", "high", 0.0, 1, "missing", "high"),
				sample("notification-01-missing.yml", "github-actions", 0.0, 1, "missing", "high", 0.7, 4, "partial", "medium", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high"),
				sampleWithNotifications("notification-02-github-slack-failure-complete.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high"),
				sampleWithNotifications("notification-03-gitlab-teams-on-failure.yml", "gitlab-ci", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 1.0, 5, "complete", "high"),
				sampleWithNotifications("notification-04-discord-webhook-partial.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.5, 3, "partial", "high"),
				sampleWithNotifications("notification-05-email-notification.yml", "github-actions", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.0, 1, "missing", "high", 0.8, 5, "complete", "high"));
	}

	private Sample sample(
			String filename,
			String provider,
			double buildScore,
			int buildLevel,
			String buildStatus,
			String buildConfidence,
			double testScore,
			int testLevel,
			String testStatus,
			String testConfidence,
			double codeAnalysisScore,
			int codeAnalysisLevel,
			String codeAnalysisStatus,
			String codeAnalysisConfidence,
			double deploymentScore,
			int deploymentLevel,
			String deploymentStatus,
			String deploymentConfidence) {
		return sampleWithArtifactsAndNotifications(
				filename,
				provider,
				buildScore,
				buildLevel,
				buildStatus,
				buildConfidence,
				testScore,
				testLevel,
				testStatus,
				testConfidence,
				codeAnalysisScore,
				codeAnalysisLevel,
				codeAnalysisStatus,
				codeAnalysisConfidence,
				0.0,
				1,
				"missing",
				"high",
				deploymentScore,
				deploymentLevel,
				deploymentStatus,
				deploymentConfidence,
				0.0,
				1,
				"missing",
				"high");
	}

	private Sample sampleWithNotifications(
			String filename,
			String provider,
			double buildScore,
			int buildLevel,
			String buildStatus,
			String buildConfidence,
			double testScore,
			int testLevel,
			String testStatus,
			String testConfidence,
			double codeAnalysisScore,
			int codeAnalysisLevel,
			String codeAnalysisStatus,
			String codeAnalysisConfidence,
			double deploymentScore,
			int deploymentLevel,
			String deploymentStatus,
			String deploymentConfidence,
			double notificationScore,
			int notificationLevel,
			String notificationStatus,
			String notificationConfidence) {
		return new Sample(
				filename,
				provider,
				buildScore,
				buildLevel,
				buildStatus,
				buildConfidence,
				testScore,
				testLevel,
				testStatus,
				testConfidence,
				codeAnalysisScore,
				codeAnalysisLevel,
				codeAnalysisStatus,
				codeAnalysisConfidence,
				0.0,
				1,
				"missing",
				"high",
				deploymentScore,
				deploymentLevel,
				deploymentStatus,
				deploymentConfidence,
				notificationScore,
				notificationLevel,
				notificationStatus,
				notificationConfidence);
	}

	private Sample sampleWithArtifacts(
			String filename,
			String provider,
			double buildScore,
			int buildLevel,
			String buildStatus,
			String buildConfidence,
			double testScore,
			int testLevel,
			String testStatus,
			String testConfidence,
			double codeAnalysisScore,
			int codeAnalysisLevel,
			String codeAnalysisStatus,
			String codeAnalysisConfidence,
			double artifactScore,
			int artifactLevel,
			String artifactStatus,
			String artifactConfidence,
			double deploymentScore,
			int deploymentLevel,
			String deploymentStatus,
			String deploymentConfidence) {
		return sampleWithArtifactsAndNotifications(
				filename,
				provider,
				buildScore,
				buildLevel,
				buildStatus,
				buildConfidence,
				testScore,
				testLevel,
				testStatus,
				testConfidence,
				codeAnalysisScore,
				codeAnalysisLevel,
				codeAnalysisStatus,
				codeAnalysisConfidence,
				artifactScore,
				artifactLevel,
				artifactStatus,
				artifactConfidence,
				deploymentScore,
				deploymentLevel,
				deploymentStatus,
				deploymentConfidence,
				0.0,
				1,
				"missing",
				"high");
	}

	private Sample sampleWithArtifactsAndNotifications(
			String filename,
			String provider,
			double buildScore,
			int buildLevel,
			String buildStatus,
			String buildConfidence,
			double testScore,
			int testLevel,
			String testStatus,
			String testConfidence,
			double codeAnalysisScore,
			int codeAnalysisLevel,
			String codeAnalysisStatus,
			String codeAnalysisConfidence,
			double artifactScore,
			int artifactLevel,
			String artifactStatus,
			String artifactConfidence,
			double deploymentScore,
			int deploymentLevel,
			String deploymentStatus,
			String deploymentConfidence,
			double notificationScore,
			int notificationLevel,
			String notificationStatus,
			String notificationConfidence) {
		return new Sample(
				filename,
				provider,
				buildScore,
				buildLevel,
				buildStatus,
				buildConfidence,
				testScore,
				testLevel,
				testStatus,
				testConfidence,
				codeAnalysisScore,
				codeAnalysisLevel,
				codeAnalysisStatus,
				codeAnalysisConfidence,
				artifactScore,
				artifactLevel,
				artifactStatus,
				artifactConfidence,
				deploymentScore,
				deploymentLevel,
				deploymentStatus,
				deploymentConfidence,
				notificationScore,
				notificationLevel,
				notificationStatus,
				notificationConfidence);
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
			String testConfidence,
			double codeAnalysisScore,
			int codeAnalysisLevel,
			String codeAnalysisStatus,
			String codeAnalysisConfidence,
			double artifactScore,
			int artifactLevel,
			String artifactStatus,
			String artifactConfidence,
			double deploymentScore,
			int deploymentLevel,
			String deploymentStatus,
			String deploymentConfidence,
			double notificationScore,
			int notificationLevel,
			String notificationStatus,
			String notificationConfidence) {
	}
}
