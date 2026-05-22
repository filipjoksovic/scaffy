package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BuildReleaseManagementCapabilityRuleSet implements CapabilityRuleSet {

	private static final String DIMENSION = "build_release";

	private static final String CAPABILITY_BUILD_SCRIPTING = "Build scripting maturity";
	private static final String CAPABILITY_DEPENDENCY_HANDLING = "Dependency handling";
	private static final String CAPABILITY_PACKAGING = "Packaging & artifacts";
	private static final String CAPABILITY_REGISTRY_PUBLISH = "Registry / release publish";
	private static final String CAPABILITY_VERSIONING = "Versioning / tagging";

	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_DOCKER = "Docker";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String TOOL_MAVEN = "Maven";
	private static final String TOOL_GRADLE = "Gradle";
	private static final String ECOSYSTEM_DOCKER_IMAGE = "Docker image";

	private static final List<CommandRule> BUILD_RULES = List.of(
			CommandRule.of("Generic", "(?:^|[;&|\\n]\\s*)(?<cmd>build)(?=\\s*$|\\s*[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+build)(?=$|\\s|[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:(?:--dir|-C|--filter)\\s+(?:\\$\\{\\{[^}]+}}|\\S+)\\s+)*(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\bpackage\\b|\\binstall\\b|\\bverify\\b))"),
			CommandRule.of(TOOL_GRADLE, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*(?:\\bbuild\\b|\\bassemble\\w*\\b))"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+(?:build|publish)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+build\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOCKER, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:buildx\\s+build|build)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+build\\b[^\\n;&|]*)"));
	private static final CommandRule DOCKER_BUILD_ACTION_RULE = CommandRule.of(ECOSYSTEM_DOCKER, "(?<cmd>.*)");

	private static final List<CommandRule> DEPENDENCY_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+ci\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+restore\\b[^\\n;&|]*)"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdependency:go-offline\\b[^\\n;&|]*)"));

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
		return DIMENSION;
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		List<CommandMatch> buildMatches = allMatches(
				CommandMatcher.findMatches(document, BUILD_RULES),
				buildActionMatches(document));

		boolean hasBuild = !buildMatches.isEmpty();
		boolean hasAutomaticTrigger = false;

		if (hasBuild) {
			CommandMatch primaryBuild = buildMatches.getFirst();
			findings.add(CapabilityFinding.positive("BUILD_STAGE_PRESENT", DIMENSION, CAPABILITY_BUILD_SCRIPTING,
					primaryBuild.evidence(), primaryBuild.location()));

			Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, buildMatches);
			if (automaticTrigger.isPresent()) {
				hasAutomaticTrigger = true;
				findings.add(CapabilityFinding.positive("BUILD_AUTOMATIC_TRIGGER", DIMENSION, CAPABILITY_BUILD_SCRIPTING,
						automaticTrigger.get().evidence(), automaticTrigger.get().location()));
			}
		}
		else {
			findings.add(CapabilityFinding.missing("BUILD_STAGE_PRESENT", DIMENSION, CAPABILITY_BUILD_SCRIPTING));
		}

		List<CommandMatch> dependencyMatches = CommandMatcher.findMatches(document, DEPENDENCY_RULES);
		Optional<CommandMatch> dependencyBeforeBuild = dependencyBeforeBuild(dependencyMatches, buildMatches);
		if (dependencyBeforeBuild.isPresent()) {
			CommandMatch dependency = dependencyBeforeBuild.get();
			if (deterministicInstall(dependency.evidence())) {
				findings.add(CapabilityFinding.positive("DETERMINISTIC_INSTALL_PRESENT", DIMENSION, CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
			}
			else {
				findings.add(CapabilityFinding.positive("DEPENDENCY_INSTALL_PRESENT", DIMENSION, CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
				findings.add(CapabilityFinding.smell("NON_DETERMINISTIC_INSTALL", DIMENSION, CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
			}
		}
		else {
			findings.add(CapabilityFinding.missing("MISSING_PACKAGE_MANAGEMENT", DIMENSION, CAPABILITY_DEPENDENCY_HANDLING));
		}

		Optional<DetectedPractice> artifactOutput = artifactOutput(document);
		boolean hasArtifact = artifactOutput.isPresent();
		if (hasArtifact) {
			findings.add(CapabilityFinding.positive("ARTIFACT_OUTPUT_PRESENT", DIMENSION, CAPABILITY_PACKAGING,
					artifactOutput.get().evidence(), artifactOutput.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("PIPELINE_MISSING_ARTIFACT_PUBLISH", DIMENSION, CAPABILITY_PACKAGING));
		}

		Optional<DetectedPractice> registryPublish = firstPractice(
				CommandMatcher.findMatches(document, REGISTRY_PUBLISH_RULES), "Package or image registry publish detected")
				.or(() -> registryPublishAction(document));
		boolean hasPublish = registryPublish.isPresent();
		if (hasPublish) {
			findings.add(CapabilityFinding.positive("REGISTRY_PUBLISH_PRESENT", DIMENSION, CAPABILITY_REGISTRY_PUBLISH,
					registryPublish.get().evidence(), registryPublish.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("NO_RELEASE_STAGE", DIMENSION, CAPABILITY_REGISTRY_PUBLISH));
		}

		Optional<DetectedPractice> artifactReuse = artifactReuse(document);
		if (artifactReuse.isPresent()) {
			findings.add(CapabilityFinding.positive("ARTIFACT_REUSE_PRESENT", DIMENSION, CAPABILITY_REGISTRY_PUBLISH,
					artifactReuse.get().evidence(), artifactReuse.get().location()));
		}

		if (hasBuild && hasAutomaticTrigger && !hasArtifact && !hasPublish) {
			CommandMatch primaryBuild = buildMatches.getFirst();
			findings.add(CapabilityFinding.smell("BUILD_ONLY_PIPELINE", DIMENSION, CAPABILITY_BUILD_SCRIPTING,
					primaryBuild.evidence(), primaryBuild.location()));
		}

		Optional<DetectedPractice> versioning = firstPractice(
				CommandMatcher.findMatches(document, VERSIONING_RULES), "Artifact identity or versioning detected")
				.or(() -> versioningAction(document));
		if (versioning.isPresent()) {
			findings.add(CapabilityFinding.positive("VERSIONED_ARTIFACT", DIMENSION, CAPABILITY_VERSIONING,
					versioning.get().evidence(), versioning.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("RELEASE_TAGGING_PRESENT", DIMENSION, CAPABILITY_VERSIONING));
		}

		return findings;
	}

	private Optional<CommandMatch> dependencyBeforeBuild(List<CommandMatch> dependencies, List<CommandMatch> builds) {
		for (CommandMatch build : builds) {
			for (CommandMatch dependency : dependencies) {
				if (!dependency.job().equals(build.job())) {
					continue;
				}
				if (dependency.step().index() < build.step().index()) {
					return Optional.of(dependency);
				}
				if (dependency.step().index() == build.step().index() && dependency.position() <= build.position()) {
					return Optional.of(dependency);
				}
			}
		}
		return Optional.empty();
	}

	private List<CommandMatch> buildActionMatches(PipelineDocument document) {
		List<CommandMatch> matches = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (dockerBuildAction(step)) {
					matches.add(new CommandMatch(job, step, step.uses(), step.location(), 0, DOCKER_BUILD_ACTION_RULE));
				}
			}
		}
		return matches;
	}

	private boolean dockerBuildAction(PipelineStep step) {
		return step.uses() != null && step.uses().toLowerCase(Locale.ROOT).startsWith("docker/build-push-action");
	}

	private List<CommandMatch> allMatches(List<CommandMatch> commandMatches, List<CommandMatch> actionMatches) {
		List<CommandMatch> matches = new ArrayList<>(commandMatches);
		for (CommandMatch actionMatch : actionMatches) {
			if (!matches.contains(actionMatch)) {
				matches.add(actionMatch);
			}
		}
		return matches;
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<CommandMatch> buildMatches) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice("Automatic trigger detected", trigger.name(), trigger.location()));
		}

		boolean automaticBuildJob = buildMatches.stream().anyMatch(match -> !match.job().manualOnly());
		if (!automaticBuildJob) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice("Automatic trigger detected", trigger.name(), trigger.location()))
				.or(() -> buildMatches.stream()
						.filter(match -> !match.job().manualOnly())
						.findFirst()
						.map(match -> new DetectedPractice(
								"Automatic trigger detected",
								"non-manual GitLab CI build job",
								match.job().location())));
	}

	private Optional<DetectedPractice> artifactOutput(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			if (!job.outputs().isEmpty()) {
				PipelineOutput output = job.outputs().getFirst();
				return Optional.of(new DetectedPractice("Artifact or archive output detected", output.evidence(), output.location()));
			}
			for (PipelineStep step : job.steps()) {
				if (uploadArtifactAction(step) || dockerBuildAction(step)) {
					return Optional.of(new DetectedPractice("Artifact or archive output detected", step.uses(), step.location()));
				}
			}
		}
		return firstPractice(CommandMatcher.findMatches(document, ARTIFACT_OUTPUT_RULES), "Artifact or archive output detected");
	}

	private Optional<DetectedPractice> artifactReuse(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (downloadArtifactAction(step)) {
					return Optional.of(new DetectedPractice("Artifact reuse or download detected", step.uses(), step.location()));
				}
			}
		}
		return firstPractice(CommandMatcher.findMatches(document, ARTIFACT_REUSE_RULES), "Artifact reuse or download detected");
	}

	private Optional<DetectedPractice> registryPublishAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (dockerBuildPushAction(step)) {
					return Optional.of(new DetectedPractice("Package or image registry publish detected", step.uses(), step.location()));
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> versioningAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (dockerBuildAction(step) && AnalysisSupport.containsAny(AnalysisSupport.lower(step.details()), "tags=", "github.sha", "github_sha", "ci_commit_sha")) {
					return Optional.of(new DetectedPractice("Artifact identity or versioning detected", step.details(), step.location()));
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

	private boolean dockerBuildPushAction(PipelineStep step) {
		return dockerBuildAction(step) && AnalysisSupport.containsAny(AnalysisSupport.lower(step.details()), "push=true", "push: true");
	}

	private boolean deterministicInstall(String evidence) {
		List<String> tokens = commandTokens(evidence);
		return startsWith(tokens, "npm", "ci")
				|| (startsWith(tokens, "pnpm", "install") && tokens.contains("--frozen-lockfile"))
				|| (startsWith(tokens, "yarn", "install") && tokens.contains("--immutable"));
	}

	private List<String> commandTokens(String command) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char character = Character.toLowerCase(command.charAt(i));
			if (Character.isWhitespace(character)) {
				if (!current.isEmpty()) {
					tokens.add(current.toString());
					current.setLength(0);
				}
			}
			else {
				current.append(character);
			}
		}
		if (!current.isEmpty()) {
			tokens.add(current.toString());
		}
		return tokens;
	}

	private boolean startsWith(List<String> tokens, String first, String second) {
		return tokens.size() >= 2 && first.equals(tokens.get(0)) && second.equals(tokens.get(1));
	}

}
