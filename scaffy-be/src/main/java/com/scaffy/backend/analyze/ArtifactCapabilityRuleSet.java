package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(28)
public class ArtifactCapabilityRuleSet implements CapabilityRuleSet {

	private static final double ARTIFACT_OUTPUT_WEIGHT = 0.30;
	private static final double REGISTRY_PUBLISH_WEIGHT = 0.25;
	private static final double ARTIFACT_REUSE_WEIGHT = 0.15;
	private static final double VERSIONING_WEIGHT = 0.15;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;

	private static final String DIMENSION = "artifacts";
	private static final String PRACTICE_ARTIFACT_OUTPUT_DETECTED = "Artifact or archive output detected";
	private static final String PRACTICE_REGISTRY_PUBLISH_DETECTED = "Package or image registry publish detected";
	private static final String PRACTICE_ARTIFACT_REUSE_DETECTED = "Artifact reuse or download detected";
	private static final String PRACTICE_VERSIONING_DETECTED = "Artifact identity or versioning detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic artifact trigger detected";

	private static final String MISSING_ARTIFACT_OUTPUT = "No artifact or archive output detected";
	private static final String MISSING_REGISTRY_PUBLISH = "No package or image registry publish detected";
	private static final String MISSING_ARTIFACT_REUSE = "No artifact reuse or download detected";
	private static final String MISSING_VERSIONING = "No artifact identity or versioning detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic artifact trigger detected";

	private static final List<CommandRule> ARTIFACT_OUTPUT_RULES = List.of(
			CommandRule.of("Archive", "(?:^|[;&|\\n]\\s*)(?<cmd>zip\\b[^\\n;&|]*)"),
			CommandRule.of("Archive", "(?:^|[;&|\\n]\\s*)(?<cmd>tar\\b[^\\n;&|]*)"),
			CommandRule.of("Java archive", "(?:^|[;&|\\n]\\s*)(?<cmd>jar\\b[^\\n;&|]*(?:\\b-c\\b|\\bcf\\b|\\bcvf\\b)[^\\n;&|]*)"),
			CommandRule.of(".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+build\\b[^\\n;&|]*)"),
			CommandRule.of("Docker image", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:buildx\\s+build|build)\\b[^\\n;&|]*)"));

	private static final List<CommandRule> REGISTRY_PUBLISH_RULES = List.of(
			CommandRule.of("Docker image", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+push\\b[^\\n;&|]*)"),
			CommandRule.of("Docker image", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+buildx\\s+build\\b[^\\n;&|]*--push[^\\n;&|]*)"),
			CommandRule.of("npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.of("Maven", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdeploy\\b[^\\n;&|]*)"),
			CommandRule.of("Gradle", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bpublish\\b[^\\n;&|]*)"),
			CommandRule.of(".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+nuget\\s+push\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>twine\\s+upload\\b[^\\n;&|]*)"));

	private static final List<CommandRule> ARTIFACT_REUSE_RULES = List.of(
			CommandRule.of("Docker image", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+pull\\b[^\\n;&|]*)"));

	private static final List<CommandRule> VERSIONING_RULES = List.of(
			CommandRule.of("Commit SHA", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:\\$GITHUB_SHA|\\$CI_COMMIT_SHA|\\$CI_COMMIT_SHORT_SHA)[^\\n;&|]*)"),
			CommandRule.of("Version tag", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:\\$GITHUB_REF_NAME|\\$CI_COMMIT_TAG|\\$VERSION|\\bsemver\\b|\\bversion\\b|image\\.tag=|(?:--tag|-t)\\s+\\S+:\\S+)[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		Optional<DetectedPractice> artifactOutput = artifactOutput(document);
		List<CommandMatch> registryPublishMatches = CommandMatcher.findMatches(document, REGISTRY_PUBLISH_RULES);
		Optional<DetectedPractice> registryPublish = firstPractice(
				registryPublishMatches,
				PRACTICE_REGISTRY_PUBLISH_DETECTED)
				.or(() -> registryPublishAction(document));
		Optional<DetectedPractice> artifactReuse = artifactReuse(document);
		Optional<DetectedPractice> versioning = firstPractice(
				CommandMatcher.findMatches(document, VERSIONING_RULES),
				PRACTICE_VERSIONING_DETECTED)
				.or(() -> versioningAction(document));

		if (artifactOutput.isEmpty() && registryPublish.isEmpty() && artifactReuse.isEmpty()) {
			return missingAnalysis();
		}

		double score = 0.0;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		if (artifactOutput.isPresent()) {
			score += ARTIFACT_OUTPUT_WEIGHT;
			detected.add(artifactOutput.get());
		}
		else {
			missing.add(MISSING_ARTIFACT_OUTPUT);
		}

		if (registryPublish.isPresent()) {
			score += REGISTRY_PUBLISH_WEIGHT;
			detected.add(registryPublish.get());
		}
		else {
			missing.add(MISSING_REGISTRY_PUBLISH);
		}

		if (artifactReuse.isPresent()) {
			score += ARTIFACT_REUSE_WEIGHT;
			detected.add(artifactReuse.get());
		}
		else {
			missing.add(MISSING_ARTIFACT_REUSE);
		}

		if (versioning.isPresent()) {
			score += VERSIONING_WEIGHT;
			detected.add(versioning.get());
		}
		else {
			missing.add(MISSING_VERSIONING);
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, artifactOutput, registryPublish);
		if (automaticTrigger.isPresent()) {
			score += AUTOMATIC_TRIGGER_WEIGHT;
			detected.add(automaticTrigger.get());
		}
		else {
			missing.add(MISSING_AUTOMATIC_TRIGGER);
		}

		double roundedScore = round(score);
		return new DimensionAnalysis(
				dimension(),
				roundedScore,
				level(roundedScore),
				roundedScore >= 0.8 ? AnalysisStatus.COMPLETE : AnalysisStatus.PARTIAL,
				confidence(artifactOutput.isPresent(), registryPublish.isPresent() || artifactReuse.isPresent()),
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
						MISSING_ARTIFACT_OUTPUT,
						MISSING_REGISTRY_PUBLISH,
						MISSING_ARTIFACT_REUSE,
						MISSING_VERSIONING,
						MISSING_AUTOMATIC_TRIGGER));
	}

	private Optional<DetectedPractice> artifactOutput(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			if (!job.outputs().isEmpty()) {
				PipelineOutput output = job.outputs().getFirst();
				return Optional.of(new DetectedPractice(PRACTICE_ARTIFACT_OUTPUT_DETECTED, output.evidence(), output.location()));
			}
			for (PipelineStep step : job.steps()) {
				if (uploadArtifactAction(step) || dockerBuildAction(step)) {
					return Optional.of(new DetectedPractice(PRACTICE_ARTIFACT_OUTPUT_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return firstPractice(CommandMatcher.findMatches(document, ARTIFACT_OUTPUT_RULES), PRACTICE_ARTIFACT_OUTPUT_DETECTED);
	}

	private Optional<DetectedPractice> artifactReuse(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (downloadArtifactAction(step)) {
					return Optional.of(new DetectedPractice(PRACTICE_ARTIFACT_REUSE_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return firstPractice(CommandMatcher.findMatches(document, ARTIFACT_REUSE_RULES), PRACTICE_ARTIFACT_REUSE_DETECTED);
	}

	private Optional<DetectedPractice> registryPublishAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (dockerBuildPushAction(step)) {
					return Optional.of(new DetectedPractice(PRACTICE_REGISTRY_PUBLISH_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> versioningAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (dockerBuildAction(step) && containsAny(lower(step.details()), "tags=", "github.sha", "github_sha", "ci_commit_sha")) {
					return Optional.of(new DetectedPractice(PRACTICE_VERSIONING_DETECTED, step.details(), step.location()));
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> firstPractice(List<CommandMatch> matches, String practice) {
		return matches.stream()
				.findFirst()
				.map(match -> new DetectedPractice(practice, match.evidence(), match.location()));
	}

	private Optional<DetectedPractice> automaticTrigger(
			PipelineDocument document,
			Optional<DetectedPractice> artifactOutput,
			Optional<DetectedPractice> registryPublish) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		Optional<PipelineJob> producingJob = automaticProducingGitLabJob(document, artifactOutput, registryPublish);
		if (producingJob.isEmpty()) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> producingJob.map(job -> new DetectedPractice(
						PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
						"non-manual GitLab CI artifact job",
						job.location())));
	}

	private Optional<PipelineJob> automaticProducingGitLabJob(
			PipelineDocument document,
			Optional<DetectedPractice> artifactOutput,
			Optional<DetectedPractice> registryPublish) {
		for (PipelineJob job : document.jobs()) {
			if (job.manualOnly()) {
				continue;
			}
			if (!job.outputs().isEmpty() || practiceInJob(artifactOutput, job) || practiceInJob(registryPublish, job)) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	private boolean practiceInJob(Optional<DetectedPractice> practice, PipelineJob job) {
		return practice
				.map(DetectedPractice::location)
				.map(location -> location.startsWith(job.location()))
				.orElse(false);
	}

	private boolean uploadArtifactAction(PipelineStep step) {
		return step.uses() != null && lower(step.uses()).startsWith("actions/upload-artifact");
	}

	private boolean downloadArtifactAction(PipelineStep step) {
		return step.uses() != null && lower(step.uses()).startsWith("actions/download-artifact");
	}

	private boolean dockerBuildAction(PipelineStep step) {
		return step.uses() != null && lower(step.uses()).startsWith("docker/build-push-action");
	}

	private boolean dockerBuildPushAction(PipelineStep step) {
		return dockerBuildAction(step) && containsAny(lower(step.details()), "push=true", "push: true");
	}

	private boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private Confidence confidence(boolean artifactOutputDetected, boolean publishOrReuseDetected) {
		if (artifactOutputDetected && publishOrReuseDetected) {
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

	private String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
