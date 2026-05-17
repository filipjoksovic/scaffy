package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeploymentCapabilityRuleSetTest {

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
	void detectsCompleteGitHubActionsKubernetesDeployment() {
		AnalysisResponse response = analyzer.analyze("deploy.yml", """
				name: Deploy
				on:
				  push:
				    branches: [main]
				jobs:
				  deploy:
				    runs-on: ubuntu-latest
				    environment:
				      name: production
				    steps:
				      - run: kubectl set image deployment/app app=ghcr.io/acme/app:$GITHUB_SHA
				      - run: kubectl rollout status deployment/app
				""");

		DimensionAnalysis deployment = deployment(response);

		assertThat(response.dimensions()).extracting(DimensionAnalysis::dimension)
				.containsExactly("build", "test", "code_analysis", "security_scanning", "artifacts", "deployment");
		assertThat(deployment.score()).isEqualTo(1.0);
		assertThat(deployment.level()).isEqualTo(5);
		assertThat(deployment.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(deployment.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(deployment)).contains(
				"kubectl set image deployment/app app=ghcr.io/acme/app:$GITHUB_SHA",
				"environment: production",
				"push",
				"kubectl rollout status deployment/app");
	}

	@Test
	void detectsGitLabHelmDeploymentWithEnvironmentAndImage() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages:
				  - deploy

				deploy-staging:
				  stage: deploy
				  environment: staging
				  script:
				    - helm upgrade --install app chart/ --set image.tag=$CI_COMMIT_SHORT_SHA
				""");

		DimensionAnalysis deployment = deployment(response);

		assertThat(deployment.score()).isEqualTo(0.85);
		assertThat(deployment.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(deployment.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(deployment.missingPractices()).contains("No post-deploy validation detected");
		assertThat(evidence(deployment)).contains("helm upgrade --install app chart/ --set image.tag=$CI_COMMIT_SHORT_SHA");
	}

	@Test
	void manualOnlyGitLabDeploymentLosesAutomaticTriggerScore() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				deploy-prod:
				  stage: deploy
				  when: manual
				  environment:
				    name: production
				  script:
				    - gcloud run deploy app --image gcr.io/acme/app:$CI_COMMIT_SHA
				    - curl https://app.example.com/health
				""");

		DimensionAnalysis deployment = deployment(response);

		assertThat(deployment.score()).isEqualTo(0.85);
		assertThat(deployment.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(deployment.missingPractices()).contains("No automatic deployment trigger detected");
	}

	@Test
	void buildOnlyAndTestOnlyPipelinesAreNotDetectedAsDeploymentPipelines() {
		String[] commands = {
				"npm run build",
				"mvn package",
				"gradle build",
				"dotnet test",
				"go test ./...",
				"pytest"
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

			DimensionAnalysis deployment = deployment(response);

			assertThat(deployment.status())
					.as("Expected no deployment detection for %s", command)
					.isEqualTo(AnalysisStatus.MISSING);
			assertThat(deployment.score()).isEqualTo(0.0);
		}
	}

	@Test
	void detectsCommonDeploymentTools() {
		String[] commands = {
				"kubectl apply -f k8s/",
				"helm upgrade --install app chart/",
				"docker compose up -d",
				"docker stack deploy -c docker-compose.yml app",
				"aws ecs update-service --cluster prod --service app --force-new-deployment",
				"gcloud run deploy app --image gcr.io/acme/app:$GITHUB_SHA",
				"az webapp deploy --name app --resource-group prod --src-path app.zip",
				"firebase deploy --only hosting",
				"vercel deploy --prod",
				"netlify deploy --prod",
				"rsync -av dist/ deploy@example.com:/var/www/app"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("deploy.yml", """
					name: Deploy
					on: [workflow_run]
					jobs:
					  deploy-production:
					    runs-on: ubuntu-latest
					    environment: production
					    steps:
					      - run: %s
					""".formatted(command));

			DimensionAnalysis deployment = deployment(response);

			assertThat(deployment.status())
					.as("Expected deployment command to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(deployment)).contains(command);
		}
	}

	private DimensionAnalysis deployment(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "deployment".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
