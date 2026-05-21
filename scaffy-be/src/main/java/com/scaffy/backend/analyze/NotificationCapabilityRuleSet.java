package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class NotificationCapabilityRuleSet implements CapabilityRuleSet {

	private static final String CAPABILITY_NOTIFICATION_CHANNEL = "Notification channel";
	private static final String CAPABILITY_STATUS_ALERTING = "Status-based alerting";

	private static final String PRACTICE_CHANNEL_DETECTED = "Notification channel or integration detected";
	private static final String PRACTICE_STATUS_CONDITION_DETECTED = "Failure or status notification condition detected";
	private static final String PRACTICE_DELIVERY_TARGET_DETECTED = "External notification delivery target detected";

	private static final String OFFICE_COM_WEBHOOK = "office.com/webhook";

	@Override
	public String dimension() {
		return "workflow_quality";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();
		List<NotificationCandidate> candidates = candidates(document);

		// Notification channel
		Optional<DetectedPractice> channel = channel(candidates);
		Optional<DetectedPractice> delivery = deliveryTarget(candidates);
		if (channel.isPresent()) {
			findings.add(CapabilityFinding.positive("NOTIFICATION_CHANNEL_PRESENT", dimension(), CAPABILITY_NOTIFICATION_CHANNEL,
					channel.get().evidence(), channel.get().location()));
		}
		else if (delivery.isPresent()) {
			findings.add(CapabilityFinding.positive("NOTIFICATION_CHANNEL_PRESENT", dimension(), CAPABILITY_NOTIFICATION_CHANNEL,
					delivery.get().evidence(), delivery.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("NOTIFICATION_MISSING", dimension(), CAPABILITY_NOTIFICATION_CHANNEL));
		}

		// Status-based alerting
		Optional<DetectedPractice> statusCondition = statusCondition(candidates);
		if (statusCondition.isPresent()) {
			findings.add(CapabilityFinding.positive("STATUS_CONDITION_PRESENT", dimension(), CAPABILITY_STATUS_ALERTING,
					statusCondition.get().evidence(), statusCondition.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("STATUS_CONDITION_MISSING", dimension(), CAPABILITY_STATUS_ALERTING));
		}

		return findings;
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

	private record NotificationCandidate(
			PipelineJob job,
			PipelineStep step,
			String evidence,
			String location,
			String context) {
	}
}
