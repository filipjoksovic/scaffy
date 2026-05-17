package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArtifactCapabilityRuleSetTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(
					new BuildCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new ArtifactCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()));

	@Test
	void detectsGitHubUploadArtifactWorkflow() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  package:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run build
				      - uses: actions/upload-artifact@v4
				        with:
				          name: dist
				          path: dist/
				""");

		DimensionAnalysis artifacts = artifacts(response);

		assertThat(response.dimensions()).extracting(DimensionAnalysis::dimension)
				.containsExactly("build", "test", "code_analysis", "artifacts", "deployment");
		assertThat(artifacts.score()).isEqualTo(0.45);
		assertThat(artifacts.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(artifacts.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(evidence(artifacts)).contains("actions/upload-artifact@v4", "push");
	}

	@Test
	void detectsGitLabArtifactPaths() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				package:
				  stage: build
				  script:
				    - npm run build
				  artifacts:
				    paths:
				      - dist/
				""");

		DimensionAnalysis artifacts = artifacts(response);

		assertThat(artifacts.score()).isEqualTo(0.45);
		assertThat(artifacts.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(evidence(artifacts)).contains("artifacts.paths", "non-manual GitLab CI artifact job");
	}

	@Test
	void detectsDockerImageBuildPushAndShaTag() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Image
				on: [push]
				jobs:
				  image:
				    runs-on: ubuntu-latest
				    steps:
				      - run: docker buildx build --push -t ghcr.io/acme/app:$GITHUB_SHA .
				      - run: docker pull ghcr.io/acme/app:$GITHUB_SHA
				""");

		DimensionAnalysis artifacts = artifacts(response);

		assertThat(artifacts.score()).isEqualTo(1.0);
		assertThat(artifacts.level()).isEqualTo(5);
		assertThat(artifacts.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(artifacts.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(artifacts)).contains("docker buildx build --push -t ghcr.io/acme/app:$GITHUB_SHA .");
	}

	@Test
	void detectsCommonPackagePublishCommands() {
		String[] commands = {
				"npm publish",
				"mvn deploy",
				"./gradlew publish",
				"dotnet nuget push pkg.nupkg --source https://api.nuget.org/v3/index.json",
				"twine upload dist/*"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("ci.yml", """
					name: Package
					on: [push]
					jobs:
					  publish:
					    runs-on: ubuntu-latest
					    steps:
					      - run: python -m build
					      - run: %s
					""".formatted(command));

			DimensionAnalysis artifacts = artifacts(response);

			assertThat(artifacts.status())
					.as("Expected package publish to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(artifacts)).contains(command);
		}
	}

	@Test
	void detectsArtifactReuseSignals() {
		AnalysisResponse githubResponse = analyzer.analyze("ci.yml", """
				name: Reuse
				on: [workflow_run]
				jobs:
				  reuse:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/download-artifact@v4
				""");

		assertThat(artifacts(githubResponse).score()).isEqualTo(0.3);
		assertThat(evidence(artifacts(githubResponse))).contains("actions/download-artifact@v4");

		AnalysisResponse dockerResponse = analyzer.analyze(".gitlab-ci.yml", """
				reuse:
				  script:
				    - docker pull registry.example.com/acme/app:$CI_COMMIT_SHA
				""");

		assertThat(artifacts(dockerResponse).score()).isEqualTo(0.3);
		assertThat(evidence(artifacts(dockerResponse))).contains("docker pull registry.example.com/acme/app:$CI_COMMIT_SHA");
	}

	@Test
	void nonArtifactPipelinesReturnMissingArtifactStatus() {
		String[] commands = {
				"npm test",
				"npm run lint",
				"kubectl apply -f k8s/",
				"curl https://app.example.com/health"
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

			DimensionAnalysis artifacts = artifacts(response);

			assertThat(artifacts.status())
					.as("Expected no artifact detection for %s", command)
					.isEqualTo(AnalysisStatus.MISSING);
			assertThat(artifacts.score()).isEqualTo(0.0);
		}
	}

	@Test
	void manualOnlyGitLabArtifactJobLosesAutomaticTriggerScore() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				package:
				  stage: build
				  when: manual
				  script:
				    - npm run build
				  artifacts:
				    paths:
				      - dist/
				""");

		DimensionAnalysis artifacts = artifacts(response);

		assertThat(artifacts.score()).isEqualTo(0.3);
		assertThat(artifacts.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(artifacts.missingPractices()).contains("No automatic artifact trigger detected");
	}

	private DimensionAnalysis artifacts(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "artifacts".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
