package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationCapabilityRuleSetTest {

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
					new DeploymentCapabilityRuleSet(),
					new NotificationCapabilityRuleSet()));

	@Test
	void detectsCompleteGitHubSlackFailureNotification() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  notify:
				    runs-on: ubuntu-latest
				    steps:
				      - name: Notify pipeline failure
				        if: failure()
				        uses: slackapi/slack-github-action@v2
				        env:
				          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(response.dimensions()).extracting(DimensionAnalysis::dimension)
				.containsExactly("build", "test", "code_analysis", "security_scanning", "artifacts", "deployment", "notifications");
		assertThat(notifications.score()).isEqualTo(1.0);
		assertThat(notifications.level()).isEqualTo(5);
		assertThat(notifications.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(notifications.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(notifications)).contains("slackapi/slack-github-action@v2", "failure()", "push");
	}

	@Test
	void detectsGitLabTeamsNotificationOnFailure() {
		AnalysisResponse response = analyzer.analyze(".gitlab-ci.yml", """
				notify_failure:
				  stage: notify
				  when: on_failure
				  variables:
				    MSTEAMS_WEBHOOK: $MSTEAMS_WEBHOOK
				  script:
				    - curl -X POST "$MSTEAMS_WEBHOOK" -H "Content-Type: application/json" -d '{"text":"Pipeline failed"}'
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.score()).isEqualTo(1.0);
		assertThat(notifications.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(notifications.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(notifications)).contains("when: on_failure", "non-manual GitLab CI notification job");
	}

	@Test
	void detectsDiscordWebhookNotification() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Notify
				on: [workflow_dispatch]
				jobs:
				  notify:
				    runs-on: ubuntu-latest
				    steps:
				      - run: curl -X POST https://discord.com/api/webhooks/abc/def -d '{"content":"Done"}'
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.score()).isEqualTo(0.5);
		assertThat(notifications.status()).isEqualTo(AnalysisStatus.PARTIAL);
		assertThat(notifications.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(evidence(notifications)).contains("curl -X POST https://discord.com/api/webhooks/abc/def -d '{\"content\":\"Done\"}'");
		assertThat(notifications.missingPractices()).contains("No automatic notification trigger detected");
	}

	@Test
	void detectsEmailNotificationCommands() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Email
				on: [push]
				jobs:
				  notify:
				    runs-on: ubuntu-latest
				    steps:
				      - run: sendmail -t team@example.com < pipeline-status.txt
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.score()).isEqualTo(0.8);
		assertThat(notifications.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(notifications)).contains("sendmail -t team@example.com < pipeline-status.txt");
	}

	@Test
	void detectsPostDeployNotificationContext() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Deploy
				on: [push]
				jobs:
				  deploy:
				    runs-on: ubuntu-latest
				    steps:
				      - run: kubectl apply -f k8s/
				      - name: Notify deployment
				        uses: slackapi/slack-github-action@v2
				        env:
				          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.score()).isEqualTo(0.8);
		assertThat(notifications.status()).isEqualTo(AnalysisStatus.COMPLETE);
		assertThat(evidence(notifications)).contains("slackapi/slack-github-action@v2", "push");
	}

	@Test
	void nonNotificationPipelinesReturnMissingNotificationStatus() {
		String[] commands = {
				"npm run build",
				"npm test",
				"npm run lint",
				"actions/upload-artifact@v4",
				"kubectl apply -f k8s/"
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

			DimensionAnalysis notifications = notifications(response);

			assertThat(notifications.status())
					.as("Expected no notification detection for %s", command)
					.isEqualTo(AnalysisStatus.MISSING);
			assertThat(notifications.score()).isEqualTo(0.0);
		}
	}

	@Test
	void manualOnlyGitHubNotificationLosesAutomaticTriggerScore() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Manual notify
				on: [workflow_dispatch]
				jobs:
				  notify:
				    runs-on: ubuntu-latest
				    steps:
				      - if: always()
				        uses: slackapi/slack-github-action@v2
				        env:
				          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.score()).isEqualTo(0.7);
		assertThat(notifications.missingPractices()).contains("No automatic notification trigger detected");
	}

	@Test
	void genericCurlWithoutWebhookDoesNotCountAsNotification() {
		AnalysisResponse response = analyzer.analyze("ci.yml", """
				name: Health
				on: [push]
				jobs:
				  smoke:
				    runs-on: ubuntu-latest
				    steps:
				      - run: curl https://app.example.com/health
				""");

		DimensionAnalysis notifications = notifications(response);

		assertThat(notifications.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(notifications.score()).isEqualTo(0.0);
	}

	private DimensionAnalysis notifications(AnalysisResponse response) {
		return response.dimensions().stream()
				.filter(dimension -> "notifications".equals(dimension.dimension()))
				.findFirst()
				.orElseThrow();
	}

	private List<String> evidence(DimensionAnalysis analysis) {
		return analysis.detectedPractices().stream()
				.map(DetectedPractice::evidence)
				.toList();
	}
}
