package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelineAnalyzerTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(new BuildReleaseManagementCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void detectsGitHubActionsNodeBuildWithCleanInstall() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on:
				  push:
				  pull_request:
				jobs:
				  frontend:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm ci
				      - run: npm run build
				""");

		DomainScore build = build(response);

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITHUB_ACTIONS);
		assertThat(response.overallStatus()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(build)).contains("npm ci", "npm run build");
	}

	@Test
	void detectsGitHubActionsBuildCommandEcosystems() {
		String[] commands = {
				"mvn --batch-mode clean verify",
				"gradle build",
				"./gradlew build",
				"dotnet build",
				"dotnet publish",
				"go build ./...",
				"docker build -t app .",
				"docker buildx build .",
				"python -m build"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("ci.yml", """
					name: CI
					on: [push]
					jobs:
					  build:
					    runs-on: ubuntu-latest
					    steps:
					      - run: %s
					""".formatted(command));

			DomainScore build = build(response);

			assertThat(build.status())
					.as("Expected build command to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(build)).contains(command);
		}
	}

	@Test
	void detectsGitLabBuildArtifactsAsOutput() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages:
				  - build

				frontend:
				  stage: build
				  image: node:24
				  script:
				    - npm ci
				    - npm run build
				  artifacts:
				    paths:
				      - dist/
				""");

		DomainScore build = build(response);

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITLAB_CI);
		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(build)).contains("npm ci", "npm run build");
	}

	@Test
	void manualOnlyGitHubWorkflowLosesAutomaticTriggerScoreAndConfidence() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on:
				  workflow_dispatch:
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm run build
				""");

		DomainScore build = build(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void manualOnlyGitLabJobLosesAutomaticTriggerScoreAndConfidence() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				build:
				  stage: build
				  when: manual
				  script:
				    - npm ci
				    - npm run build
				""");

		DomainScore build = build(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void testOnlyPipelineIsNotDetectedAsBuildPipeline() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm test
				""");

		DomainScore build = build(response);

		assertThat(response.overallScore()).isEqualTo(0.0);
		assertThat(response.overallLevel()).isEqualTo(1);
		assertThat(response.overallStatus()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(build.score()).isEqualTo(0.0);
		assertThat(build.level()).isEqualTo(1);
		assertThat(build.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(build)).isEmpty();
	}

	@Test
	void rejectsInvalidYaml() {
		assertThatThrownBy(() -> analyzer.analyze("ci.yml", """
				name: CI
				on: [push
				"""))
				.isInstanceOf(PipelineAnalysisException.class)
				.extracting("error")
				.isEqualTo("Invalid pipeline YAML");
	}

	@Test
	void rejectsUnsupportedYaml() {
		assertThatThrownBy(() -> analyzer.analyze("pipeline.yml", """
				version: "3.9"
				services:
				  app:
				    image: nginx
				"""))
				.isInstanceOf(PipelineAnalysisException.class)
				.extracting("error")
				.isEqualTo("Unsupported pipeline provider");
	}

	@Test
	void detectsGitLabFromFilename() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				build:
				  script:
				    - go build ./...
				""");

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITLAB_CI);
		assertThat(build(response).status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void parsesGitHubActionsOnKey() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				on:
				  push:
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: go build ./...
				""");

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITHUB_ACTIONS);
		assertThat(evidence(build(response))).contains("push", "go build ./...");
	}

	private DomainScore build(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "build_release".equals(dimension.dimension()))
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
}
