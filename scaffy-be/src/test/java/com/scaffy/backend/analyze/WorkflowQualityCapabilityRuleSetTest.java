package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorkflowQualityCapabilityRuleSetTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(new WorkflowQualityCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void emitsMissingTimeoutSmellWhenJobHasNoTimeout() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("MISSING_TIMEOUT");
		assertThat(positiveRuleIds(workflow)).doesNotContain("TIMEOUT_PRESENT");
	}

	@Test
	void emitsTimeoutPresentPositiveWhenAllJobsHaveTimeout() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-24.04
				    timeout-minutes: 15
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(positiveRuleIds(workflow)).contains("TIMEOUT_PRESENT");
		assertThat(smellRuleIds(workflow)).doesNotContain("MISSING_TIMEOUT");
	}

	@Test
	void emitsContinueOnErrorSmellWhenStepUsesIt() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  flaky:
				    runs-on: ubuntu-latest
				    steps:
				      - name: lint
				        run: npm run lint
				        continue-on-error: true
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("CONTINUE_ON_ERROR_USED");
	}

	@Test
	void emitsNoPathFiltersSmellWhenTriggerHasNoPaths() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("NO_PATH_FILTERS");
	}

	@Test
	void emitsDefaultJobNameSmellForGenericJobIds() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  job1:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("DEFAULT_JOB_NAME");
	}

	@Test
	void emitsUnnamedRunStepSmellWhenRunStepHasNoName() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("UNNAMED_RUN_STEP");
	}

	@Test
	void doesNotEmitUnnamedRunStepSmellWhenAllRunStepsAreNamed() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - name: install
				        run: npm ci
				      - name: test
				        run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).doesNotContain("UNNAMED_RUN_STEP");
		assertThat(positiveRuleIds(workflow)).contains("NAMED_RUN_STEPS_PRESENT");
	}

	@Test
	void emitsUnpinnedActionVersionSmellWhenUsesTagInsteadOfSha() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("UNPINNED_ACTION_VERSION");
	}

	@Test
	void emitsPinnedActionVersionsPositiveWhenAllUsesAreCommitShas() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@8e5e7e5ab8b370d6c329ec480221332ada57f0ab
				""");

		DomainScore workflow = workflow(response);

		assertThat(positiveRuleIds(workflow)).contains("PINNED_ACTION_VERSIONS");
		assertThat(smellRuleIds(workflow)).doesNotContain("UNPINNED_ACTION_VERSION");
	}

	@Test
	void emitsRunsOnLatestSmellWhenJobUsesLatestRunner() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(smellRuleIds(workflow)).contains("RUNS_ON_LATEST");
	}

	@Test
	void emitsMultiOsAndMultiVersionPositivesForMatrixPipeline() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  test:
				    runs-on: ${{ matrix.os }}
				    timeout-minutes: 20
				    strategy:
				      matrix:
				        os: [ubuntu-24.04, windows-2022, macos-14]
				        node-version: [20, 22]
				    steps:
				      - name: test
				        run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(positiveRuleIds(workflow)).contains("MULTI_OS_TEST_PRESENT", "MULTI_VERSION_TEST_PRESENT");
	}

	@Test
	void emitsCacheSignalPresentPositiveWhenSetupNodeCacheUsed() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-24.04
				    timeout-minutes: 10
				    steps:
				      - uses: actions/setup-node@v4
				        with:
				          node-version: 22
				          cache: npm
				      - name: test
				        run: npm test
				""");

		DomainScore workflow = workflow(response);

		assertThat(positiveRuleIds(workflow)).contains("CACHE_SIGNAL_PRESENT");
		assertThat(smellRuleIds(workflow)).doesNotContain("MISSING_CACHE_SIGNAL");
	}

	private DomainScore workflow(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "workflow_quality".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> positiveRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::ruleId)
				.toList();
	}

	private List<String> smellRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.SMELL)
				.map(CapabilityFinding::ruleId)
				.toList();
	}
}
