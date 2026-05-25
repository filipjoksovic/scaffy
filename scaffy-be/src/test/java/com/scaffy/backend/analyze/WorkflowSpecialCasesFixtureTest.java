package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class WorkflowSpecialCasesFixtureTest {

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
	void unpinnedActionsFixtureTriggersUnpinnedActionVersionSmell() throws IOException {
		AnalysisResponse response = analyze("workflow-02-unpinned-actions.yml");

		assertThat(ruleIds(response, FindingType.SMELL))
				.contains("UNPINNED_ACTION_VERSION");
	}

	@Test
	void timeoutMissingFixtureTriggersMissingTimeoutSmell() throws IOException {
		AnalysisResponse response = analyze("workflow-03-timeout-missing.yml");

		assertThat(ruleIds(response, FindingType.SMELL))
				.contains("MISSING_TIMEOUT");
		assertThat(ruleIds(response, FindingType.POSITIVE))
				.doesNotContain("TIMEOUT_PRESENT");
	}

	@Test
	void hardcodedSecretFixtureParsesAndProducesAllFiveDimensions() throws IOException {
		AnalysisResponse response = analyze("workflow-06-hardcoded-secret.yml");

		assertThat(dimensions(response))
				.containsExactly(
						"build_release",
						"testing_maturity",
						"workflow_quality",
						"security_integration",
						"deployment_automation");
	}

	@Test
	void rollbackSignalFixtureTriggersRollbackSignalPositive() throws IOException {
		AnalysisResponse response = analyze("workflow-08-rollback-signal.yml");

		assertThat(ruleIds(response, FindingType.POSITIVE))
				.contains("ROLLBACK_SIGNAL_PRESENT");
	}

	@Test
	void defaultJobNamesFixtureTriggersDefaultJobNameSmell() throws IOException {
		AnalysisResponse response = analyze("workflow-09-default-job-names.yml");

		assertThat(ruleIds(response, FindingType.SMELL))
				.contains("DEFAULT_JOB_NAME");
	}

	@Test
	void matrixCacheFixtureTriggersMultiOsMultiVersionAndCachePositives() throws IOException {
		AnalysisResponse response = analyze("workflow-10-matrix-cache-use.yml");

		List<String> positives = ruleIds(response, FindingType.POSITIVE);
		assertThat(positives).contains(
				"MULTI_OS_TEST_PRESENT",
				"MULTI_VERSION_TEST_PRESENT",
				"CACHE_SIGNAL_PRESENT");
	}

	@Test
	void allSpecialCaseFixturesProduceFiveDimensionsWithoutCrashing() throws IOException {
		List<String> files = List.of(
				"workflow-01-missing-permissions.yml",
				"workflow-02-unpinned-actions.yml",
				"workflow-03-timeout-missing.yml",
				"workflow-04-concurrency-present.yml",
				"workflow-05-path-filters.yml",
				"workflow-06-hardcoded-secret.yml",
				"workflow-07-policy-as-code.yml",
				"workflow-08-rollback-signal.yml",
				"workflow-09-default-job-names.yml",
				"workflow-10-matrix-cache-use.yml");

		for (String file : files) {
			AnalysisResponse response = analyze(file);
			assertThat(response.dimensions())
					.as("fixture %s should contribute all 5 dimensions", file)
					.hasSize(5);
			assertThat(response.overallStatus())
					.as("fixture %s should have a non-null overall status", file)
					.isNotNull();
		}
	}

	private AnalysisResponse analyze(String filename) throws IOException {
		String content = Files.readString(SAMPLE_DIR.resolve(filename));
		return analyzer.analyze(filename, content);
	}

	private List<String> dimensions(AnalysisResponse response) {
		return response.dimensions().stream()
				.map(d -> d.dimension().toLowerCase(Locale.ROOT))
				.toList();
	}

	private List<String> ruleIds(AnalysisResponse response, FindingType type) {
		return response.dimensions().stream()
				.flatMap(d -> d.capabilityScores().stream())
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == type)
				.map(CapabilityFinding::ruleId)
				.toList();
	}
}
