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
					new BuildReleaseManagementCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()),
			new ScoringEngine());

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

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(response.dimensions()).extracting(DomainScore::dimension)
				.containsExactly("build_release", "testing_maturity", "workflow_quality", "security_integration", "deployment_automation");
		assertThat(codeAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(codeAnalysis)).contains("npm run lint", "npm run typecheck", "prettier --check .");
	}

	@Test
	void detectsGitLabJavaStaticAnalysis() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				quality:
				  stage: test
				  script:
				    - ./gradlew check
				""");

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(codeAnalysis)).contains("./gradlew check");
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

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(codeAnalysis)).contains("ruff check .", "black --check .", "mypy src");
	}

	@Test
	void detectsGoStaticAnalysisCommands() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				static-analysis:
				  script:
				    - go vet ./...
				    - golangci-lint run
				""");

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

			DomainScore codeAnalysis = codeAnalysis(response);

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

		DomainScore actionAnalysis = codeAnalysis(actionResponse);

		assertThat(actionAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(codeAnalysis)).contains("github/super-linter@v6");
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

			DomainScore codeAnalysis = codeAnalysis(response);

			assertThat(codeAnalysis.status()).as(command).isEqualTo(AnalysisStatus.MISSING);
			assertThat(codeAnalysis.score()).as(command).isEqualTo(0.0);
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

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

		DomainScore codeAnalysis = codeAnalysis(response);

		assertThat(codeAnalysis.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(codeAnalysis)).contains("eslint . --format gitlab");
	}

	private DomainScore codeAnalysis(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "workflow_quality".equals(dimension.dimension()))
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
