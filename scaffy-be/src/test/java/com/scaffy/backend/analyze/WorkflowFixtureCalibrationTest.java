package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Calibration of expected dimension statuses for each of the 10 new workflow-* fixtures
 * (issue #54 "Posebni primeri"). Locks the analyzer's per-dimension output for these
 * minimal pipelines so that regressions in any of the 7 ruleset implementations are caught.
 */
class WorkflowFixtureCalibrationTest {

	private static final Path SAMPLE_DIR = Path.of("src/test/resources/analyze-samples");

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(
					new BuildReleaseManagementCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new WorkflowQualityCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new NotificationCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void workflow01MissingPermissions() throws IOException {
		AnalysisResponse response = analyze("workflow-01-missing-permissions.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow02UnpinnedActions() throws IOException {
		AnalysisResponse response = analyze("workflow-02-unpinned-actions.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow03TimeoutMissing() throws IOException {
		AnalysisResponse response = analyze("workflow-03-timeout-missing.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow04ConcurrencyPresent() throws IOException {
		AnalysisResponse response = analyze("workflow-04-concurrency-present.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.PARTIAL);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow05PathFilters() throws IOException {
		AnalysisResponse response = analyze("workflow-05-path-filters.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow06HardcodedSecret() throws IOException {
		AnalysisResponse response = analyze("workflow-06-hardcoded-secret.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow07PolicyAsCode() throws IOException {
		AnalysisResponse response = analyze("workflow-07-policy-as-code.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.MISSING);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.PARTIAL);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow08RollbackSignal() throws IOException {
		AnalysisResponse response = analyze("workflow-08-rollback-signal.yml");
		assertProvider(response, PipelineProvider.GITLAB_CI);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.MISSING);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.PARTIAL);
	}

	@Test
	void workflow09DefaultJobNames() throws IOException {
		AnalysisResponse response = analyze("workflow-09-default-job-names.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.PARTIAL);
		assertStatus(response, "testing_maturity", AnalysisStatus.PARTIAL);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	@Test
	void workflow10MatrixCacheUse() throws IOException {
		AnalysisResponse response = analyze("workflow-10-matrix-cache-use.yml");
		assertProvider(response, PipelineProvider.GITHUB_ACTIONS);
		assertStatus(response, "build_release", AnalysisStatus.MISSING);
		assertStatus(response, "testing_maturity", AnalysisStatus.PARTIAL);
		assertStatus(response, "workflow_quality", AnalysisStatus.PARTIAL);
		assertStatus(response, "security_integration", AnalysisStatus.MISSING);
		assertStatus(response, "deployment_automation", AnalysisStatus.MISSING);
	}

	private AnalysisResponse analyze(String filename) throws IOException {
		String content = Files.readString(SAMPLE_DIR.resolve(filename));
		return analyzer.analyze(filename, content);
	}

	private void assertProvider(AnalysisResponse response, PipelineProvider expected) {
		assertThat(response.provider()).isEqualTo(expected);
	}

	private void assertStatus(AnalysisResponse response, String dimension, AnalysisStatus expected) {
		DomainScore score = response.dimensions().stream()
				.filter(d -> dimension.equals(d.dimension()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Dimension " + dimension + " not present in response"));
		assertThat(score.status())
				.as("dimension %s for fixture", dimension)
				.isEqualTo(expected);
	}
}
