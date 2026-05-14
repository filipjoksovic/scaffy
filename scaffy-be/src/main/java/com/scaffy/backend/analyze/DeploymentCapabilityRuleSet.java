package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class DeploymentCapabilityRuleSet implements CapabilityRuleSet {

	private static final double DEPLOYMENT_COMMAND_WEIGHT = 0.35;
	private static final double ENVIRONMENT_WEIGHT = 0.15;
	private static final double ARTIFACT_IMAGE_WEIGHT = 0.20;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;
	private static final double POST_DEPLOY_VALIDATION_WEIGHT = 0.15;

	private static final String DIMENSION = "deployment";
	private static final String PRACTICE_DEPLOYMENT_COMMAND_DETECTED = "Deployment command detected";
	private static final String PRACTICE_ENVIRONMENT_DETECTED = "Deployment environment detected";
	private static final String PRACTICE_ARTIFACT_IMAGE_DETECTED = "Build artifact or image used for deployment";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic deployment trigger detected";
	private static final String PRACTICE_POST_DEPLOY_VALIDATION_DETECTED = "Post-deploy validation detected";

	private static final String MISSING_DEPLOYMENT_COMMAND = "No deployment command detected";
	private static final String MISSING_ENVIRONMENT = "No deployment environment detected";
	private static final String MISSING_ARTIFACT_IMAGE = "No build artifact or image used for deployment detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic deployment trigger detected";
	private static final String MISSING_POST_DEPLOY_VALIDATION = "No post-deploy validation detected";

	private static final List<CommandRule> DEPLOYMENT_RULES = List.of(
			CommandRule.of("Kubernetes", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+(?:apply|patch|set\\s+image)\\b[^\\n;&|]*)"),
			CommandRule.of("Kubernetes", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+rollout\\s+(?:restart|undo)\\b[^\\n;&|]*)"),
			CommandRule.of("Helm", "(?:^|[;&|\\n]\\s*)(?<cmd>helm\\s+(?:upgrade|install)\\b[^\\n;&|]*)"),
			CommandRule.of("Docker", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:compose\\s+up|stack\\s+deploy|service\\s+update)\\b[^\\n;&|]*)"),
			CommandRule.of("AWS", "(?:^|[;&|\\n]\\s*)(?<cmd>aws\\s+(?:ecs\\s+update-service|deploy\\s+create-deployment)\\b[^\\n;&|]*)"),
			CommandRule.of("GCP", "(?:^|[;&|\\n]\\s*)(?<cmd>gcloud\\s+(?:run\\s+deploy|app\\s+deploy)\\b[^\\n;&|]*)"),
			CommandRule.of("Azure", "(?:^|[;&|\\n]\\s*)(?<cmd>az\\s+(?:webapp\\s+deploy|containerapp\\s+update)\\b[^\\n;&|]*)"),
			CommandRule.of("Firebase", "(?:^|[;&|\\n]\\s*)(?<cmd>firebase\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Vercel", "(?:^|[;&|\\n]\\s*)(?<cmd>vercel\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Netlify", "(?:^|[;&|\\n]\\s*)(?<cmd>netlify\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Remote shell", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:scp|rsync|ssh)\\b[^\\n;&|]*)"));

	private static final List<CommandRule> ARTIFACT_IMAGE_RULES = List.of(
			CommandRule.of("Docker image", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+pull\\b[^\\n;&|]*)"),
			CommandRule.of("Kubernetes image", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+set\\s+image\\b[^\\n;&|]*)"),
			CommandRule.of("Image reference", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:--image|image\\.tag|\\$CI_COMMIT_SHA|\\$CI_COMMIT_SHORT_SHA|\\$GITHUB_SHA|\\$CI_REGISTRY_IMAGE|ghcr\\.io|gcr\\.io|pkg\\.dev|azurecr\\.io|amazonaws\\.com)[^\\n;&|]*)"));

	private static final List<CommandRule> VALIDATION_RULES = List.of(
			CommandRule.of("Health check", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:curl|wget)\\b[^\\n;&|]*(?:/health|healthz|readyz)[^\\n;&|]*)"),
			CommandRule.of("Kubernetes rollout", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+rollout\\s+status\\b[^\\n;&|]*)"),
			CommandRule.of("Helm test", "(?:^|[;&|\\n]\\s*)(?<cmd>helm\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of("Smoke test", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+smoke\\b[^\\n;&|]*)"),
			CommandRule.of("Playwright smoke", "(?:^|[;&|\\n]\\s*)(?<cmd>npx\\s+playwright\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of("Cypress smoke", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:npx\\s+)?cypress\\s+run\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> deploymentMatches = CommandMatcher.findMatches(document, DEPLOYMENT_RULES);
		if (deploymentMatches.isEmpty()) {
			return missingAnalysis();
		}

		double score = DEPLOYMENT_COMMAND_WEIGHT;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		CommandMatch primaryDeployment = deploymentMatches.getFirst();
		detected.add(new DetectedPractice(PRACTICE_DEPLOYMENT_COMMAND_DETECTED, primaryDeployment.evidence(), primaryDeployment.location()));

		Optional<DetectedPractice> environment = environment(deploymentMatches);
		if (environment.isPresent()) {
			score += ENVIRONMENT_WEIGHT;
			detected.add(environment.get());
		}
		else {
			missing.add(MISSING_ENVIRONMENT);
		}

		Optional<DetectedPractice> artifactImage = artifactImage(document, deploymentMatches);
		if (artifactImage.isPresent()) {
			score += ARTIFACT_IMAGE_WEIGHT;
			detected.add(artifactImage.get());
		}
		else {
			missing.add(MISSING_ARTIFACT_IMAGE);
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, deploymentMatches);
		if (automaticTrigger.isPresent()) {
			score += AUTOMATIC_TRIGGER_WEIGHT;
			detected.add(automaticTrigger.get());
		}
		else {
			missing.add(MISSING_AUTOMATIC_TRIGGER);
		}

		Optional<DetectedPractice> validation = postDeployValidation(document, deploymentMatches);
		if (validation.isPresent()) {
			score += POST_DEPLOY_VALIDATION_WEIGHT;
			detected.add(validation.get());
		}
		else {
			missing.add(MISSING_POST_DEPLOY_VALIDATION);
		}

		double roundedScore = round(score);
		return new DimensionAnalysis(
				dimension(),
				roundedScore,
				level(roundedScore),
				roundedScore >= 0.8 ? AnalysisStatus.COMPLETE : AnalysisStatus.PARTIAL,
				confidence(automaticTrigger.isPresent(), validation.isPresent()),
				detected,
				missing);
	}

	private DimensionAnalysis missingAnalysis() {
		return new DimensionAnalysis(
				dimension(),
				0.0,
				1,
				AnalysisStatus.MISSING,
				Confidence.HIGH,
				List.of(),
				List.of(
						MISSING_DEPLOYMENT_COMMAND,
						MISSING_ENVIRONMENT,
						MISSING_ARTIFACT_IMAGE,
						MISSING_AUTOMATIC_TRIGGER,
						MISSING_POST_DEPLOY_VALIDATION));
	}

	private Optional<DetectedPractice> environment(List<CommandMatch> deploymentMatches) {
		for (CommandMatch match : deploymentMatches) {
			PipelineJob job = match.job();
			if (hasText(job.environment())) {
				return Optional.of(new DetectedPractice(
						PRACTICE_ENVIRONMENT_DETECTED,
						"environment: " + job.environment(),
						job.location() + ".environment"));
			}
			Optional<String> inferredEnvironment = inferredEnvironment(job);
			if (inferredEnvironment.isPresent()) {
				return Optional.of(new DetectedPractice(
						PRACTICE_ENVIRONMENT_DETECTED,
						inferredEnvironment.get(),
						job.location()));
			}
		}
		return Optional.empty();
	}

	private Optional<String> inferredEnvironment(PipelineJob job) {
		String stage = lower(job.stage());
		if ("deploy".equals(stage) || "deployment".equals(stage)) {
			return Optional.of("stage: " + job.stage());
		}

		String jobText = lower(job.id() + " " + job.name() + " " + job.stage());
		if (containsAny(jobText, "production", "prod", "staging", "stage", "preview", "review", "development", "dev")) {
			return Optional.of(job.id());
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> artifactImage(PipelineDocument document, List<CommandMatch> deploymentMatches) {
		List<CommandMatch> imageMatches = CommandMatcher.findMatches(document, ARTIFACT_IMAGE_RULES);
		for (CommandMatch imageMatch : imageMatches) {
			if (sameDeploymentJob(imageMatch, deploymentMatches)) {
				return Optional.of(new DetectedPractice(PRACTICE_ARTIFACT_IMAGE_DETECTED, imageMatch.evidence(), imageMatch.location()));
			}
		}

		return deploymentMatches.stream()
				.filter(match -> imageSignal(match.evidence()))
				.findFirst()
				.map(match -> new DetectedPractice(PRACTICE_ARTIFACT_IMAGE_DETECTED, match.evidence(), match.location()));
	}

	private boolean imageSignal(String evidence) {
		String normalized = lower(evidence);
		return containsAny(
				normalized,
				"--image",
				"image.tag",
				"$ci_commit_sha",
				"$ci_commit_short_sha",
				"$github_sha",
				"$ci_registry_image",
				"ghcr.io",
				"gcr.io",
				"pkg.dev",
				"azurecr.io",
				"amazonaws.com");
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<CommandMatch> deploymentMatches) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		boolean automaticDeploymentJob = deploymentMatches.stream().anyMatch(match -> !match.job().manualOnly());
		if (!automaticDeploymentJob) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> deploymentMatches.stream()
						.filter(match -> !match.job().manualOnly())
						.findFirst()
						.map(match -> new DetectedPractice(
								PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
								"non-manual GitLab CI deployment job",
								match.job().location())));
	}

	private Optional<DetectedPractice> postDeployValidation(PipelineDocument document, List<CommandMatch> deploymentMatches) {
		List<CommandMatch> validationMatches = CommandMatcher.findMatches(document, VALIDATION_RULES);
		for (CommandMatch validationMatch : validationMatches) {
			if (sameDeploymentJob(validationMatch, deploymentMatches) && afterDeployment(validationMatch, deploymentMatches)) {
				return Optional.of(new DetectedPractice(
						PRACTICE_POST_DEPLOY_VALIDATION_DETECTED,
						validationMatch.evidence(),
						validationMatch.location()));
			}
		}
		return Optional.empty();
	}

	private boolean sameDeploymentJob(CommandMatch candidate, List<CommandMatch> deploymentMatches) {
		return deploymentMatches.stream().anyMatch(deployment -> deployment.job().equals(candidate.job()));
	}

	private boolean afterDeployment(CommandMatch candidate, List<CommandMatch> deploymentMatches) {
		return deploymentMatches.stream()
				.filter(deployment -> deployment.job().equals(candidate.job()))
				.anyMatch(deployment -> candidate.step().index() > deployment.step().index()
						|| candidate.step().index() == deployment.step().index() && candidate.position() >= deployment.position());
	}

	private boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private Confidence confidence(boolean automaticTriggerDetected, boolean validationDetected) {
		if (automaticTriggerDetected && validationDetected) {
			return Confidence.HIGH;
		}
		return Confidence.MEDIUM;
	}

	private int level(double score) {
		if (score == 0.0) {
			return 1;
		}
		if (score < 0.4) {
			return 2;
		}
		if (score < 0.6) {
			return 3;
		}
		if (score < 0.8) {
			return 4;
		}
		return 5;
	}

	private double round(double score) {
		return Math.round(score * 100.0) / 100.0;
	}
}
