package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TestCapabilityRuleSetTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(new BuildReleaseManagementCapabilityRuleSet(), new TestCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void detectsGitHubActionsNpmTest() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm test -- --coverage
				      - uses: actions/upload-artifact@v4
				        with:
				          name: coverage
				          path: coverage/
				""");

		DomainScore test = test(response);

		assertThat(response.dimensions()).extracting(DomainScore::dimension).containsExactly("build_release", "testing_maturity");
		assertThat(test.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(test)).contains("npm test -- --coverage", "push");
	}

	@Test
	void detectsGitHubActionsNpmRunTestUnit() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [pull_request]
				jobs:
				  unit:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run test:unit
				""");

		DomainScore test = test(response);

		assertThat(test.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(test)).contains("npm run test:unit");
	}

	@Test
	void detectsGitHubActionsE2eTools() {
		String[] commands = {
				"npx playwright test",
				"npx cypress run"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("ci.yml", """
					name: CI
					on: [push]
					jobs:
					  e2e:
					    runs-on: ubuntu-latest
					    steps:
					      - run: %s
					""".formatted(command));

			DomainScore test = test(response);

			assertThat(test.status())
					.as("Expected e2e test command to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(test)).contains(command);
		}
	}

	@Test
	void detectsGitLabTestCommandEcosystems() {
		String[] commands = {
				"mvn test",
				"mvn verify",
				"gradle test",
				"./gradlew check",
				"dotnet test",
				"go test ./...",
				"pytest",
				"python -m pytest"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
					test:
					  script:
					    - %s
					""".formatted(command));

			DomainScore test = test(response);

			assertThat(test.status())
					.as("Expected test command to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(test)).contains(command);
		}
	}

	@Test
	void buildOnlyPipelineReturnsMissingTestStatus() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm run build
				""");

		DomainScore test = test(response);

		assertThat(test.score()).isEqualTo(0.0);
		assertThat(test.level()).isEqualTo(1);
		assertThat(test.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(test)).isEmpty();
	}

	@Test
	void manualOnlyGitHubTestsLoseAutomaticTriggerScoreAndConfidence() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on:
				  workflow_dispatch:
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore test = test(response);

		assertThat(test.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void manualOnlyGitLabTestsLoseAutomaticTriggerScoreAndConfidence() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				test:
				  when: manual
				  script:
				    - dotnet test
				""");

		DomainScore test = test(response);

		assertThat(test.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void detectsGitLabJUnitArtifactsAndCoverageQualitySignals() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages:
				  - test

				test:
				  stage: test
				  script:
				    - pytest --junitxml=report.xml --cov=app
				  artifacts:
				    reports:
				      junit: report.xml
				    paths:
				      - htmlcov/
				""");

		DomainScore test = test(response);

		assertThat(test.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(test)).contains("pytest --junitxml=report.xml --cov=app");
	}

	@Test
	void manualOnlyGitLabTestJobsProduceManualOnlyTestJobSmell() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				test:
				  when: manual
				  script:
				    - npm test
				""");

		DomainScore test = test(response);

		assertThat(smellRuleIds(test)).contains("MANUAL_ONLY_TEST_JOB");
	}

	@Test
	void automaticGitLabTestJobDoesNotProduceManualOnlyTestJobSmell() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				test:
				  script:
				    - npm test
				""");

		DomainScore test = test(response);

		assertThat(smellRuleIds(test)).doesNotContain("MANUAL_ONLY_TEST_JOB");
	}

	@Test
	void testsWithoutCoverageProduceNoCoverageToolMissing() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test
				""");

		DomainScore test = test(response);

		assertThat(missingRuleIds(test)).contains("NO_COVERAGE_TOOL");
	}

	@Test
	void testsWithCoverageDoNotProduceNoCoverageToolMissing() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm test -- --coverage
				""");

		DomainScore test = test(response);

		assertThat(missingRuleIds(test)).doesNotContain("NO_COVERAGE_TOOL");
	}

	private DomainScore test(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "testing_maturity".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::evidence)
				.toList();
	}

	private List<String> smellRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.SMELL)
				.map(CapabilityFinding::ruleId)
				.toList();
	}

	private List<String> missingRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.MISSING)
				.map(CapabilityFinding::ruleId)
				.toList();
	}
}
