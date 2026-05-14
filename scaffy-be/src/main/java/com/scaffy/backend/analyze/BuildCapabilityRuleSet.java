package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BuildCapabilityRuleSet implements CapabilityRuleSet {

	private static final double BUILD_COMMAND_WEIGHT = 0.35;
	private static final double DEPENDENCY_INSTALL_WEIGHT = 0.20;
	private static final double ECOSYSTEM_WEIGHT = 0.15;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;
	private static final double BUILD_OUTPUT_WEIGHT = 0.15;

	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_DOCKER = "Docker";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String TOOL_MAVEN = "Maven";
	private static final String TOOL_GRADLE = "Gradle";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic trigger detected";
	private static final String PRACTICE_BUILD_OUTPUT_DETECTED = "Build output detected";

	private static final List<CommandRule> BUILD_RULES = List.of(
			CommandRule.of("Generic", "(?:^|[;&|\\n]\\s*)(?<cmd>build)(?=\\s*$|\\s*[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+build)(?=$|\\s|[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\bpackage\\b|\\binstall\\b|\\bverify\\b))"),
			CommandRule.of(TOOL_GRADLE, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bbuild\\b)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+(?:build|publish)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+build\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOCKER, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:buildx\\s+build|build)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+build\\b[^\\n;&|]*)"));

	private static final List<CommandRule> DEPENDENCY_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+ci\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+install\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+restore\\b[^\\n;&|]*)"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdependency:go-offline\\b[^\\n;&|]*)"));

	private static final List<CommandRule> PUBLISH_RULES = List.of(
			CommandRule.of("npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdeploy\\b[^\\n;&|]*)"),
			CommandRule.of(TOOL_GRADLE, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bpublish\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+nuget\\s+push\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>twine\\s+upload\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOCKER, "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+push\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return "build";
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> buildMatches = CommandMatcher.findMatches(document, BUILD_RULES);
		if (buildMatches.isEmpty()) {
			return new DimensionAnalysis(
					dimension(),
					0.0,
					1,
					AnalysisStatus.MISSING,
					Confidence.HIGH,
					List.of(),
					List.of(
							"No build command detected",
							"No dependency install/restore before build detected",
							"No build ecosystem detected",
							"No automatic build trigger detected",
							"No explicit build artifact detected"));
		}

		double score = BUILD_COMMAND_WEIGHT;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		CommandMatch primaryBuild = buildMatches.getFirst();
		detected.add(new DetectedPractice("Build command detected", primaryBuild.evidence(), primaryBuild.location()));

		List<CommandMatch> dependencyMatches = CommandMatcher.findMatches(document, DEPENDENCY_RULES);
		Optional<CommandMatch> dependencyBeforeBuild = dependencyBeforeBuild(dependencyMatches, buildMatches);
		if (dependencyBeforeBuild.isPresent()) {
			CommandMatch dependency = dependencyBeforeBuild.get();
			score += DEPENDENCY_INSTALL_WEIGHT;
			Map<String, String> metadata = deterministicInstall(dependency.evidence())
					? Map.of("deterministic", "true")
					: Map.of("deterministic", "false");
			String practice = deterministicInstall(dependency.evidence())
					? "Clean dependency install detected"
					: "Dependency install/restore detected";
			detected.add(new DetectedPractice(practice, dependency.evidence(), dependency.location(), metadata));
		}
		else {
			missing.add("No dependency install/restore before build detected");
		}

		Set<String> ecosystems = ecosystems(buildMatches, dependencyMatches);
		if (ecosystems.isEmpty()) {
			missing.add("No build ecosystem detected");
		}
		else {
			score += ECOSYSTEM_WEIGHT;
			detected.add(new DetectedPractice("Build ecosystem detected", String.join(", ", ecosystems), primaryBuild.location()));
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, buildMatches);
		if (automaticTrigger.isPresent()) {
			score += AUTOMATIC_TRIGGER_WEIGHT;
			detected.add(automaticTrigger.get());
		}
		else {
			missing.add("No automatic build trigger detected");
		}

		Optional<DetectedPractice> buildOutput = buildOutput(document, buildMatches);
		if (buildOutput.isPresent()) {
			score += BUILD_OUTPUT_WEIGHT;
			detected.add(buildOutput.get());
		}
		else {
			missing.add("No explicit build artifact detected");
		}

		double roundedScore = round(score);
		return new DimensionAnalysis(
				dimension(),
				roundedScore,
				level(roundedScore),
				roundedScore >= 0.8 ? AnalysisStatus.COMPLETE : AnalysisStatus.PARTIAL,
				confidence(automaticTrigger.isPresent()),
				detected,
				missing);
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

	private Set<String> ecosystems(List<CommandMatch> buildMatches, List<CommandMatch> dependencyMatches) {
		Set<String> ecosystems = new LinkedHashSet<>();
		for (CommandMatch buildMatch : buildMatches) {
			ecosystems.add(buildMatch.rule().ecosystem());
		}
		for (CommandMatch dependencyMatch : dependencyMatches) {
			ecosystems.add(dependencyMatch.rule().ecosystem());
		}
		return ecosystems;
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<CommandMatch> buildMatches) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		boolean automaticBuildJob = buildMatches.stream().anyMatch(match -> !match.job().manualOnly());
		if (!automaticBuildJob) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> buildMatches.stream()
						.filter(match -> !match.job().manualOnly())
						.findFirst()
						.map(match -> new DetectedPractice(
								PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
								"non-manual GitLab CI build job",
								match.job().location())));
	}

	private Optional<DetectedPractice> buildOutput(PipelineDocument document, List<CommandMatch> buildMatches) {
		Optional<CommandMatch> dockerBuild = buildMatches.stream()
				.filter(match -> ECOSYSTEM_DOCKER.equals(match.rule().ecosystem()))
				.findFirst();
		if (dockerBuild.isPresent()) {
			CommandMatch match = dockerBuild.get();
			return Optional.of(new DetectedPractice(PRACTICE_BUILD_OUTPUT_DETECTED, match.evidence(), match.location()));
		}

		for (CommandMatch buildMatch : buildMatches) {
			if (!buildMatch.job().outputs().isEmpty()) {
				PipelineOutput output = buildMatch.job().outputs().getFirst();
				return Optional.of(new DetectedPractice(PRACTICE_BUILD_OUTPUT_DETECTED, output.evidence(), output.location()));
			}
			for (PipelineStep step : buildMatch.job().steps()) {
				if (step.uses() != null && step.uses().toLowerCase(Locale.ROOT).startsWith("actions/upload-artifact")) {
					return Optional.of(new DetectedPractice(PRACTICE_BUILD_OUTPUT_DETECTED, step.uses(), step.location()));
				}
			}
		}

		List<CommandMatch> publishMatches = CommandMatcher.findMatches(document, PUBLISH_RULES);
		for (CommandMatch publishMatch : publishMatches) {
			boolean sameBuildJob = buildMatches.stream().anyMatch(buildMatch -> buildMatch.job().equals(publishMatch.job()));
			if (sameBuildJob) {
				return Optional.of(new DetectedPractice(PRACTICE_BUILD_OUTPUT_DETECTED, publishMatch.evidence(), publishMatch.location()));
			}
		}

		return Optional.empty();
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

	private Confidence confidence(boolean automaticTriggerDetected) {
		return automaticTriggerDetected ? Confidence.HIGH : Confidence.MEDIUM;
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
