package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelineAnalyzerRealWorldCalibrationTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(
					new BuildReleaseManagementCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet(),
					new NotificationCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void matureGitHubActionsPipelineScoresAboveModerate() {
		AnalysisResponse response = analyzer.analyze(".github/workflows/node-api.yml", """
				name: Node API CI/CD
				on:
				  pull_request:
				  push:
				    branches: [main]
				jobs:
				  test:
				    runs-on: ubuntu-24.04
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm ci
				      - run: npm run lint
				      - run: npm test -- --coverage
				      - uses: actions/upload-artifact@v4
				        with:
				          name: coverage
				          path: coverage/
				  build:
				    needs: test
				    runs-on: ubuntu-24.04
				    steps:
				      - uses: actions/checkout@v4
				      - uses: docker/build-push-action@v6
				        with:
				          context: .
				          push: true
				          tags: ghcr.io/example/node-api:${{ github.sha }}
				      - uses: aquasecurity/trivy-action@0.24.0
				        with:
				          image-ref: ghcr.io/example/node-api:${{ github.sha }}
				  deploy-production:
				    needs: build
				    runs-on: ubuntu-24.04
				    environment: production
				    steps:
				      - run: kubectl set image deployment/node-api api=ghcr.io/example/node-api:${{ github.sha }} -n production
				      - run: kubectl rollout status deployment/node-api -n production --timeout=300s
				""");

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITHUB_ACTIONS);
		assertThat(response.overallScore()).isGreaterThan(0.0);
		assertThat(dimension(response, "build_release").status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(dimension(response, "security_integration").status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void matureGitLabPipelineScoresAboveModerate() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				stages: [prepare, test, security, package, deploy]

				bundle:
				  stage: prepare
				  image: ruby:3.3
				  script:
				    - bundle install --jobs 4 --retry 3

				rspec:
				  stage: test
				  image: ruby:3.3
				  script:
				    - bundle exec rspec --format RspecJunitFormatter --out rspec.xml
				  artifacts:
				    reports:
				      junit: rspec.xml

				brakeman:
				  stage: security
				  image: ruby:3.3
				  script:
				    - bundle exec brakeman --no-pager --exit-on-warn

				container_scan:
				  stage: security
				  image: docker:27
				  script:
				    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA .
				    - trivy image --exit-code 1 --severity HIGH,CRITICAL $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

				build_image:
				  stage: package
				  image: docker:27
				  script:
				    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA .
				    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

				production:
				  stage: deploy
				  environment:
				    name: production
				  script:
				    - helm upgrade --install app charts/app --namespace production --set image.tag=$CI_COMMIT_SHA --atomic
				    - helm test app --namespace production
				""");

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITLAB_CI);
		assertThat(response.overallScore()).isGreaterThan(0.0);
		assertThat(dimension(response, "testing_maturity").status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(dimension(response, "security_integration").status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void selectiveMonorepoGitHubActionsPipelineNoLongerScoresMissing() {
		AnalysisResponse response = analyzer.analyze(".github/workflows/monorepo.yml", """
				name: Monorepo CI
				on:
				  pull_request:
				  push:
				    branches: [main]
				jobs:
				  test:
				    runs-on: ubuntu-24.04
				    strategy:
				      matrix:
				        package: [services/api, apps/web]
				    steps:
				      - uses: actions/checkout@v4
				      - run: pnpm install --frozen-lockfile
				      - run: pnpm --dir ${{ matrix.package }} test
				      - run: pnpm --dir ${{ matrix.package }} build
				  deploy-api:
				    needs: test
				    if: github.ref == 'refs/heads/main'
				    runs-on: ubuntu-24.04
				    environment: production-api
				    steps:
				      - run: ./scripts/deploy-api.sh ${{ github.sha }}
				""");

		assertThat(response.provider()).isEqualTo(PipelineProvider.GITHUB_ACTIONS);
		assertThat(response.overallScore()).isGreaterThan(0.0);
		assertThat(dimension(response, "build_release").status()).isNotEqualTo(AnalysisStatus.MISSING);
		assertThat(dimension(response, "testing_maturity").status()).isNotEqualTo(AnalysisStatus.MISSING);
	}

	@Test
	void azurePipelinesYamlIsRejectedInsteadOfMisclassifiedAsGitLabCi() {
		assertThatThrownBy(() -> analyzer.analyze("azure-pipelines.yml", """
				trigger:
				  branches:
				    include:
				      - main
				stages:
				  - stage: BuildAndTest
				    jobs:
				      - job: test
				        steps:
				          - script: dotnet restore
				          - script: dotnet test --logger trx
				  - stage: DeployProduction
				    jobs:
				      - deployment: production
				        environment: production
				        strategy:
				          runOnce:
				            deploy:
				              steps:
				                - script: ./scripts/deploy.sh production $(Build.SourceVersion)
				"""))
				.isInstanceOf(PipelineAnalysisException.class)
				.extracting("error")
				.isEqualTo("Unsupported pipeline provider");
	}

	private DomainScore dimension(AnalysisResponse response, String dimension) {
		return response.dimensions().stream()
				.filter(candidate -> dimension.equals(candidate.dimension()))
				.findFirst()
				.orElseThrow();
	}
}
