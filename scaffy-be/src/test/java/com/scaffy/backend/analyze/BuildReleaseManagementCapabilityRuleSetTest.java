package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BuildReleaseManagementCapabilityRuleSetTest {

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

		DomainScore build = buildRelease(response);

		assertThat(response.dimensions()).extracting(DomainScore::dimension)
				.containsExactly("build_release", "testing_maturity", "workflow_quality", "security_integration", "deployment_automation");
		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(positiveEvidence(build)).contains("actions/upload-artifact@v4", "push");
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

		DomainScore build = buildRelease(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(positiveEvidence(build)).contains("artifacts.paths");
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

		DomainScore build = buildRelease(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(positiveEvidence(build)).contains("docker buildx build --push -t ghcr.io/acme/app:$GITHUB_SHA .");
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

			DomainScore build = buildRelease(response);

			assertThat(build.status())
					.as("Expected package publish to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(positiveEvidence(build)).contains(command);
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

		assertThat(positiveEvidence(buildRelease(githubResponse))).contains("actions/download-artifact@v4");

		AnalysisResponse dockerResponse = analyzer.analyze(".gitlab-ci.yml", """
				reuse:
				  script:
				    - docker pull registry.example.com/acme/app:$CI_COMMIT_SHA
				""");

		assertThat(positiveEvidence(buildRelease(dockerResponse))).contains("docker pull registry.example.com/acme/app:$CI_COMMIT_SHA");
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

			DomainScore build = buildRelease(response);

			assertThat(build.status())
					.as("Expected no artifact detection for %s", command)
					.isEqualTo(AnalysisStatus.MISSING);
			assertThat(build.score()).isEqualTo(0.0);
		}
	}

	@Test
	void buildOnlyPipelineReceivesBuildOnlyPipelineSmell() {
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

		DomainScore build = buildRelease(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(smellRuleIds(build)).contains("BUILD_ONLY_PIPELINE");
	}

	@Test
	void pipelineWithArtifactOutputDoesNotReceiveBuildOnlySmell() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm run build
				      - uses: actions/upload-artifact@v4
				        with:
				          name: dist
				          path: dist/
				""");

		DomainScore build = buildRelease(response);

		assertThat(smellRuleIds(build)).doesNotContain("BUILD_ONLY_PIPELINE");
	}

	@Test
	void pipelineWithRegistryPublishDoesNotReceiveBuildOnlySmell() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run build
				      - run: npm publish
				""");

		DomainScore build = buildRelease(response);

		assertThat(smellRuleIds(build)).doesNotContain("BUILD_ONLY_PIPELINE");
	}

	@Test
	void buildOnlyPipelineScoresLowerThanPublishPipeline() {
		AnalysisResponse buildOnly = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run build
				""");

		AnalysisResponse withPublish = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm run build
				      - run: npm publish
				""");

		double buildOnlyScore = buildRelease(buildOnly).score();
		double withPublishScore = buildRelease(withPublish).score();

		assertThat(buildOnlyScore).isLessThan(withPublishScore);
	}

	@Test
	void detectsDeterministicDependencyInstall() {
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

		DomainScore build = buildRelease(response);

		assertThat(positiveEvidence(build)).contains("npm ci");
		assertThat(smellRuleIds(build)).doesNotContain("NON_DETERMINISTIC_INSTALL");
	}

	@Test
	void nonDeterministicInstallProducesSmell() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm install
				      - run: npm run build
				""");

		DomainScore build = buildRelease(response);

		assertThat(smellRuleIds(build)).contains("NON_DETERMINISTIC_INSTALL");
	}

	@Test
	void detectsVersioningSignals() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - run: docker build -t app:$GITHUB_SHA .
				      - run: docker push app:$GITHUB_SHA
				""");

		DomainScore build = buildRelease(response);

		assertThat(positiveRuleIds(build)).contains("VERSIONED_ARTIFACT");
	}

	@Test
	void manualOnlyGitLabArtifactJobStillDetectsArtifact() {
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

		DomainScore build = buildRelease(response);

		assertThat(build.status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	private DomainScore buildRelease(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(d -> "build_release".equals(d.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> positiveEvidence(DomainScore score) {
		return score.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::evidence)
				.toList();
	}

	private List<String> positiveRuleIds(DomainScore score) {
		return score.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::ruleId)
				.toList();
	}

	private List<String> smellRuleIds(DomainScore score) {
		return score.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.SMELL)
				.map(CapabilityFinding::ruleId)
				.toList();
	}

}
