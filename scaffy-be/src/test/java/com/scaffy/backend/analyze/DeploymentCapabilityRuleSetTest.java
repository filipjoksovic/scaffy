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
					new BuildReleaseManagementCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()),
			new ScoringEngine());

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

		DomainScore deployment = deployment(response);

		assertThat(response.dimensions()).extracting(DomainScore::dimension)
				.containsExactly("build_release", "testing_maturity", "workflow_quality", "security_integration", "deployment_automation");
		assertThat(deployment.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(evidence(deployment)).contains(
				"kubectl set image deployment/app app=ghcr.io/acme/app:$GITHUB_SHA",
				"environment: production",
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

		DomainScore deployment = deployment(response);

		assertThat(deployment.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

		DomainScore deployment = deployment(response);

		assertThat(deployment.status()).isNotEqualTo(AnalysisStatus.MISSING);
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

			DomainScore deployment = deployment(response);

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

			DomainScore deployment = deployment(response);

			assertThat(deployment.status())
					.as("Expected deployment command to be detected for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(evidence(deployment)).contains(command);
		}
	}

	@Test
	void detectsTerraformAsIacTool() {
		AnalysisResponse response = analyzer.analyze("deploy.yml", """
				name: Deploy
				on: [push]
				jobs:
				  infra:
				    runs-on: ubuntu-latest
				    environment: production
				    steps:
				      - run: terraform init
				      - run: terraform apply -auto-approve
				""");

		DomainScore deployment = deployment(response);

		assertThat(deployment.status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(positiveRuleIds(deployment)).contains("IAC_PRESENT");
	}

	@Test
	void detectsAnsibleAndPulumiAsIacTools() {
		String[] commands = {
				"ansible-playbook deploy.yml",
				"pulumi up --yes"
		};

		for (String command : commands) {
			AnalysisResponse response = analyzer.analyze("deploy.yml", """
					name: Deploy
					on: [push]
					jobs:
					  deploy:
					    runs-on: ubuntu-latest
					    environment: production
					    steps:
					      - run: %s
					""".formatted(command));

			DomainScore deployment = deployment(response);

			assertThat(deployment.status())
					.as("Expected IaC tool detection for %s", command)
					.isNotEqualTo(AnalysisStatus.MISSING);
			assertThat(positiveRuleIds(deployment))
					.as("Expected IAC_PRESENT for %s", command)
					.contains("IAC_PRESENT");
		}
	}

	@Test
	void multiStageGitLabPipelineEmitsMultiStagePositive() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages:
				  - build
				  - deploy

				build:
				  stage: build
				  script:
				    - npm run build

				deploy:
				  stage: deploy
				  environment: production
				  script:
				    - kubectl apply -f k8s/
				""");

		DomainScore deployment = deployment(response);

		assertThat(positiveRuleIds(deployment)).contains("MULTI_STAGE_PIPELINE_PRESENT");
		assertThat(smellRuleIds(deployment)).doesNotContain("MONOLITHIC_BUILD_PIPELINE");
	}

	@Test
	void singleJobDeploymentEmitsMonolithicBuildPipelineSmell() {
		AnalysisResponse response = analyzer.analyze("deploy.yml", """
				name: Deploy
				on: [push]
				jobs:
				  deploy:
				    runs-on: ubuntu-latest
				    environment: production
				    steps:
				      - run: kubectl apply -f k8s/
				""");

		DomainScore deployment = deployment(response);

		assertThat(smellRuleIds(deployment)).contains("MONOLITHIC_BUILD_PIPELINE");
		assertThat(positiveRuleIds(deployment)).doesNotContain("MULTI_STAGE_PIPELINE_PRESENT");
	}

	private DomainScore deployment(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "deployment_automation".equals(dimension.dimension()))
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

	private List<String> positiveRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::ruleId)
				.toList();
	}

	private List<String> smellRuleIds(DomainScore analysis) {
		return analysis.capabilityScores().stream()
				.flatMap(cs -> cs.findings().stream())
				.filter(f -> f.type() == FindingType.SMELL)
				.map(CapabilityFinding::ruleId)
				.toList();
	}
}
