package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TestCapabilityRuleSetTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(new BuildCapabilityRuleSet(), new TestCapabilityRuleSet()));

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

		DimensionAnalysis test = test(response);

		assertThat(response.dimensions()).extracting(DimensionAnalysis::dimension).containsExactly("build", "test");
		assertThat(test.score()).isEqualTo(1.0);
		assertThat(test.level()).isEqualTo(5);
		assertThat(test.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(test.confidence()).isEqualTo(Confidence.HIGH);
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

		DimensionAnalysis test = test(response);

		assertThat(test.score()).isEqualTo(0.7);
		assertThat(test.status()).isEqualTo(AnalysisStatus.PARTIAL);
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

			DimensionAnalysis test = test(response);

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

			DimensionAnalysis test = test(response);

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

		DimensionAnalysis test = test(response);

		assertThat(test.score()).isEqualTo(0.0);
		assertThat(test.level()).isEqualTo(1);
		assertThat(test.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(test.detectedPractices()).isEmpty();
		assertThat(test.missingPractices()).contains("No automated test command detected");
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

		DimensionAnalysis test = test(response);

		assertThat(test.score()).isEqualTo(0.5);
		assertThat(test.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(test.missingPractices()).contains("No automatic test trigger detected");
	}

	@Test
	void manualOnlyGitLabTestsLoseAutomaticTriggerScoreAndConfidence() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				test:
				  when: manual
				  script:
				    - dotnet test
				""");

		DimensionAnalysis test = test(response);

		assertThat(test.score()).isEqualTo(0.5);
		assertThat(test.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(test.missingPractices()).contains("No automatic test trigger detected");
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

		DimensionAnalysis test = test(response);

		assertThat(test.score()).isEqualTo(1.0);
		assertThat(test.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(test)).contains("pytest --junitxml=report.xml --cov=app");
	}

	private DimensionAnalysis test(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "test".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
