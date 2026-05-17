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
			List.of(new BuildCapabilityRuleSet()));

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

		DimensionAnalysis build = build(response);

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITHUB_ACTIONS);
		assertThat(response.overallScore()).isEqualTo(0.85);
		assertThat(response.overallLevel()).isEqualTo(5);
		assertThat(response.overallStatus()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(response.overallConfidence()).isEqualTo(Confidence.HIGH);
		assertThat(build.score()).isEqualTo(0.85);
		assertThat(build.level()).isEqualTo(5);
		assertThat(build.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(build.confidence()).isEqualTo(Confidence.HIGH);
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

			DimensionAnalysis build = build(response);

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

		DimensionAnalysis build = build(response);

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITLAB_CI);
		assertThat(build.score()).isEqualTo(1.0);
		assertThat(build.level()).isEqualTo(5);
		assertThat(build.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(build)).contains("npm ci", "npm run build", "artifacts.paths");
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

		DimensionAnalysis build = build(response);

		assertThat(build.score()).isEqualTo(0.7);
		assertThat(build.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(build.missingPractices()).contains("No automatic build trigger detected");
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

		DimensionAnalysis build = build(response);

		assertThat(build.score()).isEqualTo(0.7);
		assertThat(build.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(build.missingPractices()).contains("No automatic build trigger detected");
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

		DimensionAnalysis build = build(response);

		assertThat(response.overallScore()).isEqualTo(0.0);
		assertThat(response.overallLevel()).isEqualTo(1);
		assertThat(response.overallStatus()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(response.overallConfidence()).isEqualTo(Confidence.HIGH);
		assertThat(build.score()).isEqualTo(0.0);
		assertThat(build.level()).isEqualTo(1);
		assertThat(build.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(build.detectedPractices()).isEmpty();
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
		assertThat(build(response).status()).isEqualTo(AnalysisStatus.PARTIAL);
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

	private DimensionAnalysis build(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "build".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
