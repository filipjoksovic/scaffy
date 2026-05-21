package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(28)
public class ArtifactCapabilityRuleSet implements CapabilityRuleSet {

	private static final String CAPABILITY_PACKAGING = "Packaging & artifacts";
	private static final String CAPABILITY_REGISTRY_PUBLISH = "Registry / release publish";
	private static final String CAPABILITY_VERSIONING = "Versioning / tagging";

	private static final String PRACTICE_ARTIFACT_OUTPUT_DETECTED = "Artifact or archive output detected";
	private static final String PRACTICE_REGISTRY_PUBLISH_DETECTED = "Package or image registry publish detected";
	private static final String PRACTICE_ARTIFACT_REUSE_DETECTED = "Artifact reuse or download detected";
	private static final String PRACTICE_VERSIONING_DETECTED = "Artifact identity or versioning detected";

	private static final String ECOSYSTEM_DOCKER_IMAGE = "Docker image";

	private static final List<CommandRule> ARTIFACT_OUTPUT_RULES = List.of(
			CommandRule.of("Archive", "(?:^|[;&|\\n]\\s*)(?<cmd>zip\\b[^\\n;&|]*)"),
			CommandRule.of("Archive", "(?:^|[;&|\\n]\\s*)(?<cmd>tar\\b[^\\n;&|]*)"),
			CommandRule.of("Java archive", "(?:^|[;&|\\n]\\s*)(?<cmd>jar\\b[^\\n;&|]*(?:\\b-c\\b|\\bcf\\b|\\bcvf\\b)[^\\n;&|]*)"),
			CommandRule.of(".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+build\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOCKER_IMAGE, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:buildx\\s+build|build)\\b[^\\n;&|]*)"));

	private static final List<CommandRule> REGISTRY_PUBLISH_RULES = List.of(
			CommandRule.of(ECOSYSTEM_DOCKER_IMAGE, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+push\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOCKER_IMAGE, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+buildx\\s+build\\b[^\\n;&|]*--push[^\\n;&|]*)"),
			CommandRule.of("npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.of("Maven", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdeploy\\b[^\\n;&|]*)"),
			CommandRule.of("Gradle", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bpublish\\b[^\\n;&|]*)"),
			CommandRule.of(".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+nuget\\s+push\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>twine\\s+upload\\b[^\\n;&|]*)"));

	private static final List<CommandRule> ARTIFACT_REUSE_RULES = List.of(
			CommandRule.of(ECOSYSTEM_DOCKER_IMAGE, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+pull\\b[^\\n;&|]*)"));

	private static final List<CommandRule> VERSIONING_RULES = List.of(
			CommandRule.of("Commit SHA", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:\\$GITHUB_SHA|\\$CI_COMMIT_SHA|\\$CI_COMMIT_SHORT_SHA)[^\\n;&|]*)"),
			CommandRule.of("Version tag", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:\\$GITHUB_REF_NAME|\\$CI_COMMIT_TAG|\\$VERSION|\\bsemver\\b|\\bversion\\b|image\\.tag=|(?:--tag|-t)\\s+\\S+:\\S+)[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return "build_release";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		Optional<DetectedPractice> artifactOutput = artifactOutput(document);
		if (artifactOutput.isPresent()) {
			findings.add(CapabilityFinding.positive("ARTIFACT_OUTPUT_PRESENT", dimension(), CAPABILITY_PACKAGING,
					artifactOutput.get().evidence(), artifactOutput.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("PIPELINE_MISSING_ARTIFACT_PUBLISH", dimension(), CAPABILITY_PACKAGING));
		}

		Optional<DetectedPractice> registryPublish = firstPractice(
				CommandMatcher.findMatches(document, REGISTRY_PUBLISH_RULES), PRACTICE_REGISTRY_PUBLISH_DETECTED)
				.or(() -> registryPublishAction(document));
		if (registryPublish.isPresent()) {
			findings.add(CapabilityFinding.positive("REGISTRY_PUBLISH_PRESENT", dimension(), CAPABILITY_REGISTRY_PUBLISH,
					registryPublish.get().evidence(), registryPublish.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("NO_RELEASE_STAGE", dimension(), CAPABILITY_REGISTRY_PUBLISH));
		}

		Optional<DetectedPractice> artifactReuse = artifactReuse(document);
		if (artifactReuse.isPresent()) {
			findings.add(CapabilityFinding.positive("ARTIFACT_REUSE_PRESENT", dimension(), CAPABILITY_REGISTRY_PUBLISH,
					artifactReuse.get().evidence(), artifactReuse.get().location()));
		}

		Optional<DetectedPractice> versioning = firstPractice(
				CommandMatcher.findMatches(document, VERSIONING_RULES), PRACTICE_VERSIONING_DETECTED)
				.or(() -> versioningAction(document));
		if (versioning.isPresent()) {
			findings.add(CapabilityFinding.positive("VERSIONED_ARTIFACT", dimension(), CAPABILITY_VERSIONING,
					versioning.get().evidence(), versioning.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("RELEASE_TAGGING_PRESENT", dimension(), CAPABILITY_VERSIONING));
		}

		return findings;
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
				if (dockerBuildAction(step) && AnalysisSupport.containsAny(AnalysisSupport.lower(step.details()), "tags=", "github.sha", "github_sha", "ci_commit_sha")) {
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

	private boolean uploadArtifactAction(PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("actions/upload-artifact");
	}

	private boolean downloadArtifactAction(PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("actions/download-artifact");
	}

	private boolean dockerBuildAction(PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("docker/build-push-action");
	}

	private boolean dockerBuildPushAction(PipelineStep step) {
		return dockerBuildAction(step) && AnalysisSupport.containsAny(AnalysisSupport.lower(step.details()), "push=true", "push: true");
	}

}
