package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class NotificationCapabilityRuleSet implements CapabilityRuleSet {

	private static final double CHANNEL_WEIGHT = 0.35;
	private static final double STATUS_CONDITION_WEIGHT = 0.20;
	private static final double DELIVERY_TARGET_WEIGHT = 0.15;
	private static final double PIPELINE_CONTEXT_WEIGHT = 0.15;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;

	private static final String DIMENSION = "notifications";
	private static final String PRACTICE_CHANNEL_DETECTED = "Notification channel or integration detected";
	private static final String PRACTICE_STATUS_CONDITION_DETECTED = "Failure or status notification condition detected";
	private static final String PRACTICE_DELIVERY_TARGET_DETECTED = "External notification delivery target detected";
	private static final String PRACTICE_PIPELINE_CONTEXT_DETECTED = "Deployment or pipeline-result notification context detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic notification trigger detected";

	private static final String MISSING_CHANNEL = "No notification channel or integration detected";
	private static final String MISSING_STATUS_CONDITION = "No failure or status notification condition detected";
	private static final String MISSING_DELIVERY_TARGET = "No external notification delivery target detected";
	private static final String MISSING_PIPELINE_CONTEXT = "No deployment or pipeline-result notification context detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic notification trigger detected";

	private static final String KEYWORD_DEPLOY = "deploy";
	private static final String KEYWORD_RELEASE = "release";
	private static final String OFFICE_COM_WEBHOOK = "office.com/webhook";

	private static final List<CommandRule> DEPLOYMENT_RULES = List.of(
			CommandRule.of("Kubernetes", "(?:^|[;&|\\n]\\s*)(?<cmd>kubectl\\s+(?:apply|patch|set\\s+image|rollout\\s+(?:restart|undo|status))\\b[^\\n;&|]*)"),
			CommandRule.of("Helm", "(?:^|[;&|\\n]\\s*)(?<cmd>helm\\s+(?:upgrade|install)\\b[^\\n;&|]*)"),
			CommandRule.of("Docker", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:compose\\s+up|stack\\s+deploy|service\\s+update)\\b[^\\n;&|]*)"),
			CommandRule.of("Cloud", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:aws\\s+(?:ecs\\s+update-service|deploy\\s+create-deployment)|gcloud\\s+(?:run\\s+deploy|app\\s+deploy)|az\\s+(?:webapp\\s+deploy|containerapp\\s+update))\\b[^\\n;&|]*)"),
			CommandRule.of("PaaS", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:firebase|vercel|netlify)\\s+deploy\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<NotificationCandidate> candidates = candidates(document);
		Optional<DetectedPractice> channel = channel(candidates);
		Optional<DetectedPractice> deliveryTarget = deliveryTarget(candidates);

		if (channel.isEmpty() && deliveryTarget.isEmpty()) {
			return missingAnalysis();
		}

		double score = 0.0;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		if (channel.isPresent()) {
			score += CHANNEL_WEIGHT;
			detected.add(channel.get());
		}
		else {
			missing.add(MISSING_CHANNEL);
		}

		Optional<DetectedPractice> statusCondition = statusCondition(candidates);
		if (statusCondition.isPresent()) {
			score += STATUS_CONDITION_WEIGHT;
			detected.add(statusCondition.get());
		}
		else {
			missing.add(MISSING_STATUS_CONDITION);
		}

		if (deliveryTarget.isPresent()) {
			score += DELIVERY_TARGET_WEIGHT;
			detected.add(deliveryTarget.get());
		}
		else {
			missing.add(MISSING_DELIVERY_TARGET);
		}

		Optional<DetectedPractice> pipelineContext = pipelineContext(document, candidates);
		if (pipelineContext.isPresent()) {
			score += PIPELINE_CONTEXT_WEIGHT;
			detected.add(pipelineContext.get());
		}
		else {
			missing.add(MISSING_PIPELINE_CONTEXT);
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, candidates);
		if (automaticTrigger.isPresent()) {
			score += AUTOMATIC_TRIGGER_WEIGHT;
			detected.add(automaticTrigger.get());
		}
		else {
			missing.add(MISSING_AUTOMATIC_TRIGGER);
		}

		double roundedScore = AnalysisSupport.round(score);
		return new DimensionAnalysis(
				dimension(),
				roundedScore,
				AnalysisSupport.level(roundedScore),
				AnalysisSupport.status(roundedScore),
				confidence(channel.isPresent(), statusCondition.isPresent(), deliveryTarget.isPresent()),
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
						MISSING_CHANNEL,
						MISSING_STATUS_CONDITION,
						MISSING_DELIVERY_TARGET,
						MISSING_PIPELINE_CONTEXT,
						MISSING_AUTOMATIC_TRIGGER));
	}

	private List<NotificationCandidate> candidates(PipelineDocument document) {
		List<NotificationCandidate> candidates = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				String context = AnalysisSupport.lower(AnalysisSupport.context(job, step));
				if (notificationContext(context)) {
					candidates.add(new NotificationCandidate(job, step, evidence(step), step.location(), context));
				}
			}
		}
		return candidates;
	}

	private Optional<DetectedPractice> channel(List<NotificationCandidate> candidates) {
		return candidates.stream()
				.filter(candidate -> channelSignal(candidate.context()))
				.findFirst()
				.map(candidate -> new DetectedPractice(PRACTICE_CHANNEL_DETECTED, candidate.evidence(), candidate.location()));
	}

	private Optional<DetectedPractice> statusCondition(List<NotificationCandidate> candidates) {
		for (NotificationCandidate candidate : candidates) {
			Optional<String> condition = conditionEvidence(candidate);
			if (condition.isPresent()) {
				return Optional.of(new DetectedPractice(
						PRACTICE_STATUS_CONDITION_DETECTED,
						condition.get(),
						conditionLocation(candidate)));
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> deliveryTarget(List<NotificationCandidate> candidates) {
		return candidates.stream()
				.filter(candidate -> deliverySignal(candidate.context()))
				.findFirst()
				.map(candidate -> new DetectedPractice(PRACTICE_DELIVERY_TARGET_DETECTED, candidate.evidence(), candidate.location()));
	}

	private Optional<DetectedPractice> pipelineContext(PipelineDocument document, List<NotificationCandidate> candidates) {
		for (NotificationCandidate candidate : candidates) {
			if (AnalysisSupport.containsAny(candidate.context(), KEYWORD_DEPLOY, "deployment", KEYWORD_RELEASE, "pipeline", "workflow", "status")) {
				return Optional.of(new DetectedPractice(
						PRACTICE_PIPELINE_CONTEXT_DETECTED,
						contextEvidence(candidate),
						candidate.job().location()));
			}
		}

		List<CommandMatch> deployments = CommandMatcher.findMatches(document, DEPLOYMENT_RULES);
		if (!deployments.isEmpty() && !candidates.isEmpty()) {
			CommandMatch deployment = deployments.getFirst();
			return Optional.of(new DetectedPractice(
					PRACTICE_PIPELINE_CONTEXT_DETECTED,
					deployment.evidence(),
					deployment.location()));
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<NotificationCandidate> candidates) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		Optional<NotificationCandidate> automaticCandidate = candidates.stream()
				.filter(candidate -> !candidate.job().manualOnly())
				.findFirst();
		if (automaticCandidate.isEmpty()) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> automaticCandidate.map(candidate -> new DetectedPractice(
						PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
						"non-manual GitLab CI notification job",
						candidate.job().location())));
	}

	private boolean notificationContext(String context) {
		if (channelSignal(context) || deliverySignal(context)) {
			return true;
		}
		return AnalysisSupport.containsAny(context, "notify", "notification", "alert", "pipeline status")
				&& (context.contains("curl") || context.contains("webhook") || context.contains("post"));
	}

	private boolean channelSignal(String context) {
		return AnalysisSupport.containsAny(
				context,
				"slack",
				"chat.postmessage",
				"slackapi/slack-github-action",
				"8398a7/action-slack",
				"rtcamp/action-slack-notify",
				"teams",
				"msteams",
				OFFICE_COM_WEBHOOK,
				"discord",
				"sendgrid",
				"sendmail",
				"mailx",
				"smtp",
				"ses send-email")
				|| explicitWebhookPost(context);
	}

	private boolean deliverySignal(String context) {
		return AnalysisSupport.containsAny(
				context,
				"hooks.slack.com/services",
				"slack_webhook",
				"slack_webhook_url",
				"msteams_webhook",
				"teams_webhook",
				OFFICE_COM_WEBHOOK,
				"discord_webhook",
				"discord.com/api/webhooks",
				"webhook_url",
				"sendgrid_api_key",
				"sendmail",
				"mailx",
				"smtp",
				"ses send-email")
				|| explicitWebhookPost(context);
	}

	private boolean explicitWebhookPost(String context) {
		return AnalysisSupport.containsAny(context, "webhook", "hooks.slack.com", "discord.com/api/webhooks", OFFICE_COM_WEBHOOK)
				&& AnalysisSupport.containsAny(context, "curl", "post", "-x post", "--request post");
	}

	private Optional<String> conditionEvidence(NotificationCandidate candidate) {
		if (statusConditionText(candidate.step().condition())) {
			return Optional.of(candidate.step().condition());
		}
		if (statusConditionText(candidate.job().when())) {
			return Optional.of("when: " + candidate.job().when());
		}
		if (statusConditionText(candidate.job().condition())) {
			return Optional.of(candidate.job().condition());
		}
		if (AnalysisSupport.containsAny(candidate.context(), "on_failure", "failure()", "always()", "cancelled()", "success()")) {
			return Optional.of(candidate.evidence());
		}
		return Optional.empty();
	}

	private boolean statusConditionText(String value) {
		String text = AnalysisSupport.lower(value);
		return AnalysisSupport.containsAny(text, "failure()", "always()", "cancelled()", "success()", "on_failure", "failed", "failure");
	}

	private String conditionLocation(NotificationCandidate candidate) {
		if (statusConditionText(candidate.step().condition())) {
			return candidate.step().location() + ".if";
		}
		if (statusConditionText(candidate.job().when())) {
			return candidate.job().location() + ".when";
		}
		if (statusConditionText(candidate.job().condition())) {
			return candidate.job().location() + ".rules";
		}
		return candidate.location();
	}

	private String contextEvidence(NotificationCandidate candidate) {
		if (AnalysisSupport.hasText(candidate.job().stage()) && AnalysisSupport.containsAny(AnalysisSupport.lower(candidate.job().stage()), KEYWORD_DEPLOY, KEYWORD_RELEASE)) {
			return "stage: " + candidate.job().stage();
		}
		if (AnalysisSupport.containsAny(AnalysisSupport.lower(candidate.job().id()), KEYWORD_DEPLOY, KEYWORD_RELEASE, "notify", "notification")) {
			return candidate.job().id();
		}
		return candidate.evidence();
	}

	private String evidence(PipelineStep step) {
		if (AnalysisSupport.hasText(step.uses())) {
			return step.uses();
		}
		if (AnalysisSupport.hasText(step.command())) {
			return step.command();
		}
		if (AnalysisSupport.hasText(step.details())) {
			return step.details();
		}
		return step.location();
	}

	private Confidence confidence(boolean channelDetected, boolean statusConditionDetected, boolean deliveryTargetDetected) {
		if (channelDetected && (statusConditionDetected || deliveryTargetDetected)) {
			return Confidence.HIGH;
		}
		return Confidence.MEDIUM;
	}

	private record NotificationCandidate(
			PipelineJob job,
			PipelineStep step,
			String evidence,
			String location,
			String context) {
	}
}
