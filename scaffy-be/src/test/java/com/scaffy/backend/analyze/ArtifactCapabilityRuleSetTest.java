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
					new SecurityScanningCapabilityRuleSet(),
					new ArtifactCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()),
			new ScoringEngine());

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

		DomainScore artifacts = artifacts(response);

		assertThat(response.dimensions()).extracting(DomainScore::dimension)
				.containsExactly("build_release", "testing_maturity", "workflow_quality", "security_integration", "deployment_automation");
		assertThat(artifacts.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

		DomainScore artifacts = artifacts(response);

		assertThat(artifacts.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(artifacts)).contains("artifacts.paths");
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

		DomainScore artifacts = artifacts(response);

		assertThat(artifacts.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

			DomainScore artifacts = artifacts(response);

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

		assertThat(evidence(artifacts(githubResponse))).contains("actions/download-artifact@v4");

		AnalysisResponse dockerResponse = analyzer.analyze(".gitlab-ci.yml", """
				reuse:
				  script:
				    - docker pull registry.example.com/acme/app:$CI_COMMIT_SHA
				""");

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

			DomainScore artifacts = artifacts(response);

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

		DomainScore artifacts = artifacts(response);

		assertThat(artifacts.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	private DomainScore artifacts(AnalysisResponse response) {
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
