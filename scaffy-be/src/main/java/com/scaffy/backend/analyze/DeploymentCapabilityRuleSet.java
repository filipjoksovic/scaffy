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

	private static final String CAPABILITY_DEPLOYMENT_STAGE = "Deployment stage presence";
	private static final String CAPABILITY_ENVIRONMENT_TARGETING = "Environment targeting";
	private static final String CAPABILITY_IAC_USAGE = "IaC usage";
	private static final String CAPABILITY_ORCHESTRATION = "Orchestration maturity";
	private static final String CAPABILITY_ROLLBACK_CONTROLLED = "Rollback / controlled rollout";

	private static final String PRACTICE_DEPLOYMENT_COMMAND_DETECTED = "Deployment command detected";
	private static final String PRACTICE_ENVIRONMENT_DETECTED = "Deployment environment detected";
	private static final String PRACTICE_ARTIFACT_IMAGE_DETECTED = "Build artifact or image used for deployment";
	private static final String PRACTICE_IAC_TOOL_DETECTED = "Infrastructure-as-code tool detected";
	private static final String PRACTICE_POST_DEPLOY_VALIDATION_DETECTED = "Post-deploy validation detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic deployment trigger detected";
	private static final String PRACTICE_MULTI_STAGE_DETECTED = "Multi-stage pipeline structure detected";

	private static final List<CommandRule> DEPLOYMENT_RULES = List.of(
			CommandRule.of("Kubernetes", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+(?:apply|patch|set\\s+image)\\b[^\\n;&|]*)"),
			CommandRule.of("Kubernetes", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+rollout\\s+(?:restart|undo)\\b[^\\n;&|]*)"),
			CommandRule.of("Helm", "(?:^|[;&|\\n]\\s*)(?<cmd>helm\\s+(?:upgrade|install)\\b[^\\n;&|]*)"),
			CommandRule.of("Terraform", "(?:^|[;&|\\n]\\s*)(?<cmd>terraform\\s+(?:apply|destroy)\\b[^\\n;&|]*)"),
			CommandRule.of("Ansible", "(?:^|[;&|\\n]\\s*)(?<cmd>ansible-playbook\\b[^\\n;&|]*)"),
			CommandRule.of("Pulumi", "(?:^|[;&|\\n]\\s*)(?<cmd>pulumi\\s+(?:up|destroy)\\b[^\\n;&|]*)"),
			CommandRule.of("Docker", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:compose\\s+up|stack\\s+deploy|service\\s+update)\\b[^\\n;&|]*)"),
			CommandRule.of("AWS", "(?:^|[;&|\\n]\\s*)(?<cmd>aws\\s+(?:ecs\\s+update-service|deploy\\s+create-deployment)\\b[^\\n;&|]*)"),
			CommandRule.of("GCP", "(?:^|[;&|\\n]\\s*)(?<cmd>gcloud\\s+(?:run\\s+deploy|app\\s+deploy)\\b[^\\n;&|]*)"),
			CommandRule.of("Azure", "(?:^|[;&|\\n]\\s*)(?<cmd>az\\s+(?:webapp\\s+deploy|containerapp\\s+update)\\b[^\\n;&|]*)"),
			CommandRule.of("Firebase", "(?:^|[;&|\\n]\\s*)(?<cmd>firebase\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Vercel", "(?:^|[;&|\\n]\\s*)(?<cmd>vercel\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Netlify", "(?:^|[;&|\\n]\\s*)(?<cmd>netlify\\s+deploy\\b[^\\n;&|]*)"),
			CommandRule.of("Deployment script", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?(?:scripts/)?[^\\s;&|]*deploy[^\\s;&|]*\\.sh\\b[^\\n;&|]*)"),
			CommandRule.of("Remote shell", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:scp|rsync|ssh)\\b[^\\n;&|]*)"));

	private static final List<CommandRule> IAC_TOOL_RULES = List.of(
			CommandRule.of("Terraform", "(?:^|[;&|\\n]\\s*)(?<cmd>terraform\\s+\\w+\\b[^\\n;&|]*)"),
			CommandRule.of("Ansible", "(?:^|[;&|\\n]\\s*)(?<cmd>ansible-playbook\\b[^\\n;&|]*)"),
			CommandRule.of("Pulumi", "(?:^|[;&|\\n]\\s*)(?<cmd>pulumi\\s+\\w+\\b[^\\n;&|]*)"),
			CommandRule.of("Helm", "(?:^|[;&|\\n]\\s*)(?<cmd>helm\\s+(?:upgrade|install|template|lint)\\b[^\\n;&|]*)"));

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
		return "deployment_automation";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();
		List<CommandMatch> deploymentMatches = CommandMatcher.findMatches(document, DEPLOYMENT_RULES);

		// Deployment stage presence
		if (!deploymentMatches.isEmpty()) {
			CommandMatch match = deploymentMatches.getFirst();
			findings.add(CapabilityFinding.positive("DEPLOYMENT_STAGE_PRESENT", dimension(), CAPABILITY_DEPLOYMENT_STAGE,
					match.evidence(), match.location()));
		}
		else {
			findings.add(CapabilityFinding.missing("NO_DEPLOYMENT_STAGE", dimension(), CAPABILITY_DEPLOYMENT_STAGE));
			findings.add(CapabilityFinding.missing("MISSING_ENVIRONMENT_DECLARATION", dimension(), CAPABILITY_ENVIRONMENT_TARGETING));
			findings.add(CapabilityFinding.missing("IaC_NOT_PRESENT", dimension(), CAPABILITY_IAC_USAGE));
			findings.add(CapabilityFinding.missing("MULTI_STAGE_PIPELINE_PRESENT", dimension(), CAPABILITY_ORCHESTRATION));
			findings.add(CapabilityFinding.missing("NO_ROLLBACK_ON_FAILURE", dimension(), CAPABILITY_ROLLBACK_CONTROLLED));
			return findings;
		}

		CommandMatch primaryDeployment = deploymentMatches.getFirst();

		// Environment targeting
		Optional<DetectedPractice> environment = environment(deploymentMatches);
		if (environment.isPresent()) {
			findings.add(CapabilityFinding.positive("ENVIRONMENT_DECLARED", dimension(), CAPABILITY_ENVIRONMENT_TARGETING,
					environment.get().evidence(), environment.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("MISSING_ENVIRONMENT_DECLARATION", dimension(), CAPABILITY_ENVIRONMENT_TARGETING));
		}

		// IaC usage: IaC tools (terraform/ansible/pulumi/helm) and/or container image usage
		Optional<DetectedPractice> iacTool = iacTool(document);
		Optional<DetectedPractice> artifactImage = artifactImage(document, deploymentMatches);
		if (iacTool.isPresent()) {
			findings.add(CapabilityFinding.positive("IAC_PRESENT", dimension(), CAPABILITY_IAC_USAGE,
					iacTool.get().evidence(), iacTool.get().location()));
		}
		if (artifactImage.isPresent()) {
			findings.add(CapabilityFinding.positive("ARTIFACT_IMAGE_USED", dimension(), CAPABILITY_IAC_USAGE,
					artifactImage.get().evidence(), artifactImage.get().location()));
		}
		if (iacTool.isEmpty() && artifactImage.isEmpty()) {
			findings.add(CapabilityFinding.missing("IaC_NOT_PRESENT", dimension(), CAPABILITY_IAC_USAGE));
		}

		// Orchestration maturity: multi-stage pipeline vs monolithic
		int stages = stageCount(document);
		if (stages >= 2) {
			findings.add(CapabilityFinding.positive("MULTI_STAGE_PIPELINE_PRESENT", dimension(), CAPABILITY_ORCHESTRATION,
					PRACTICE_MULTI_STAGE_DETECTED + " (" + stages + " stages/jobs)", primaryDeployment.job().location()));
		}
		else {
			findings.add(CapabilityFinding.smell("MONOLITHIC_BUILD_PIPELINE", dimension(), CAPABILITY_ORCHESTRATION,
					primaryDeployment.evidence(), primaryDeployment.job().location()));
		}

		// Rollback / controlled rollout
		Optional<DetectedPractice> validation = postDeployValidation(document, deploymentMatches);
		if (validation.isPresent()) {
			findings.add(CapabilityFinding.positive("ROLLBACK_SIGNAL_PRESENT", dimension(), CAPABILITY_ROLLBACK_CONTROLLED,
					validation.get().evidence(), validation.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("NO_ROLLBACK_ON_FAILURE", dimension(), CAPABILITY_ROLLBACK_CONTROLLED));
		}

		return findings;
	}

	private Optional<DetectedPractice> iacTool(PipelineDocument document) {
		List<CommandMatch> matches = CommandMatcher.findMatches(document, IAC_TOOL_RULES);
		return matches.stream()
				.findFirst()
				.map(match -> new DetectedPractice(PRACTICE_IAC_TOOL_DETECTED, match.evidence(), match.location()));
	}

	private int stageCount(PipelineDocument document) {
		return (int) document.jobs().stream()
				.map(job -> hasText(job.stage()) ? job.stage() : job.id())
				.distinct()
				.count();
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
				"github.sha",
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

}
