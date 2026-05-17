package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CodeAnalysisCapabilityRuleSetTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(
					new BuildCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new ArtifactCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()));

	@Test
	void detectsCompleteGitHubActionsTypescriptCodeAnalysis() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [pull_request]
				jobs:
				  quality:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run lint
				      - run: npm run typecheck
				      - run: prettier --check .
				      - uses: actions/upload-artifact@v4
				        with:
				          name: code-quality
				          path: reports/code-quality.json
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(response.dimensions()).extracting(DimensionAnalysis::dimension)
				.containsExactly("build", "test", "code_analysis", "security_scanning", "artifacts", "deployment");
		assertThat(codeAnalysis.score()).isEqualTo(1.0);
		assertThat(codeAnalysis.level()).isEqualTo(5);
		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(codeAnalysis.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(codeAnalysis)).contains("npm run lint", "npm run typecheck", "prettier --check .", "pull_request");
	}

	@Test
	void detectsGitLabJavaStaticAnalysis() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				quality:
				  stage: test
				  script:
				    - ./gradlew check
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.score()).isEqualTo(0.45);
		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(codeAnalysis.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(evidence(codeAnalysis)).contains("./gradlew check");
		assertThat(codeAnalysis.missingPractices()).contains("No formatter or style check detected");
	}

	@Test
	void detectsPythonLintFormattingAndTypeChecking() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  quality:
				    runs-on: ubuntu-latest
				    steps:
				      - run: ruff check .
				      - run: black --check .
				      - run: mypy src
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.score()).isEqualTo(0.85);
		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(codeAnalysis)).contains("ruff check .", "black --check .", "mypy src", "push");
	}

	@Test
	void detectsGoStaticAnalysisCommands() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				static-analysis:
				  script:
				    - go vet ./...
				    - golangci-lint run
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(codeAnalysis.score()).isEqualTo(0.65);
		assertThat(evidence(codeAnalysis)).contains("go vet ./...");
	}

	@Test
	void detectsSonarCommandsAndActions() {
		String[] signals = {
				"sonar-scanner -Dsonar.projectKey=scaffy",
				"dotnet sonarscanner begin /k:scaffy"
		};

		for (String signal : signals) {
			AnalysisResponse response = analyzer.analyze("ci.yml", """
					name: CI
					on: [push]
					jobs:
					  analysis:
					    runs-on: ubuntu-latest
					    steps:
					      - run: %s
					""".formatted(signal));

			DimensionAnalysis codeAnalysis = codeAnalysis(response);

			assertThat(codeAnalysis.status())
					.as("Expected Sonar signal to be detected for %s", signal)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(codeAnalysis)).contains(signal);
		}

		AnalysisResponse actionResponse = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  analysis:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: sonarsource/sonarcloud-github-action@v2
				""");

		DimensionAnalysis actionAnalysis = codeAnalysis(actionResponse);

		assertThat(actionAnalysis.score()).isEqualTo(0.8);
		assertThat(actionAnalysis.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(actionAnalysis)).contains("sonarsource/sonarcloud-github-action@v2");
	}

	@Test
	void detectsGithubActionBasedLinting() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  lint:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: github/super-linter@v6
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.score()).isEqualTo(0.45);
		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(codeAnalysis)).contains("github/super-linter@v6", "push");
	}

	@Test
	void buildTestAndDeployPipelinesAreNotDetectedAsCodeAnalysis() {
		String[] commands = {
				"npm run build",
				"mvn package",
				"npm test",
				"dotnet test",
				"kubectl apply -f k8s/",
				"gcloud run deploy app --image gcr.io/acme/app:$GITHUB_SHA"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("ci.yml", """
					name: CI
					on: [push]
					jobs:
					  ci:
					    runs-on: ubuntu-latest
					    steps:
					      - run: %s
					""".formatted(command));

			DimensionAnalysis codeAnalysis = codeAnalysis(response);

			assertThat(codeAnalysis.status())
					.as("Expected no code analysis detection for %s", command)
					.isEqualTo(AnalysisStatus.MISSING);
			assertThat(codeAnalysis.score()).isEqualTo(0.0);
		}
	}

	@Test
	void manualOnlyWorkflowLosesAutomaticTriggerScore() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on:
				  workflow_dispatch:
				jobs:
				  quality:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run lint
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.score()).isEqualTo(0.3);
		assertThat(codeAnalysis.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(codeAnalysis.missingPractices()).contains("No automatic code analysis trigger detected");
	}

	@Test
	void detectsGitLabCodeQualityArtifact() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages:
				  - test

				code_quality:
				  stage: test
				  script:
				    - eslint . --format gitlab
				  artifacts:
				    reports:
				      codequality: gl-code-quality-report.json
				""");

		DimensionAnalysis codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.score()).isEqualTo(0.6);
		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(codeAnalysis)).contains("eslint . --format gitlab", "artifacts.reports.codequality");
	}

	private DimensionAnalysis codeAnalysis(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "code_analysis".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
