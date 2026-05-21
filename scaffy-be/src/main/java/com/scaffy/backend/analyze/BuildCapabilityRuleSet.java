package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BuildCapabilityRuleSet implements CapabilityRuleSet {

	private static final String CAPABILITY_BUILD_SCRIPTING = "Build scripting maturity";
	private static final String CAPABILITY_DEPENDENCY_HANDLING = "Dependency handling";

	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_DOCKER = "Docker";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String TOOL_MAVEN = "Maven";
	private static final String TOOL_GRADLE = "Gradle";

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

	@Override
	public String dimension() {
		return "build_release";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		List<CommandMatch> buildMatches = allMatches(
				CommandMatcher.findMatches(document, BUILD_RULES),
				buildActionMatches(document));

		if (buildMatches.isEmpty()) {
			findings.add(CapabilityFinding.missing("BUILD_STAGE_PRESENT", dimension(), CAPABILITY_BUILD_SCRIPTING));
			findings.add(CapabilityFinding.missing("MISSING_PACKAGE_MANAGEMENT", dimension(), CAPABILITY_DEPENDENCY_HANDLING));
			return findings;
		}

		CommandMatch primaryBuild = buildMatches.getFirst();
		findings.add(CapabilityFinding.positive("BUILD_STAGE_PRESENT", dimension(), CAPABILITY_BUILD_SCRIPTING,
				primaryBuild.evidence(), primaryBuild.location()));

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, buildMatches);
		if (automaticTrigger.isPresent()) {
			findings.add(CapabilityFinding.positive("BUILD_AUTOMATIC_TRIGGER", dimension(), CAPABILITY_BUILD_SCRIPTING,
					automaticTrigger.get().evidence(), automaticTrigger.get().location()));
		}

		List<CommandMatch> dependencyMatches = CommandMatcher.findMatches(document, DEPENDENCY_RULES);
		Optional<CommandMatch> dependencyBeforeBuild = dependencyBeforeBuild(dependencyMatches, buildMatches);
		if (dependencyBeforeBuild.isPresent()) {
			CommandMatch dependency = dependencyBeforeBuild.get();
			if (deterministicInstall(dependency.evidence())) {
				findings.add(CapabilityFinding.positive("DETERMINISTIC_INSTALL_PRESENT", dimension(), CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
			}
			else {
				findings.add(CapabilityFinding.positive("DEPENDENCY_INSTALL_PRESENT", dimension(), CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
				findings.add(CapabilityFinding.smell("NON_DETERMINISTIC_INSTALL", dimension(), CAPABILITY_DEPENDENCY_HANDLING,
						dependency.evidence(), dependency.location()));
			}
		}
		else {
			findings.add(CapabilityFinding.missing("MISSING_PACKAGE_MANAGEMENT", dimension(), CAPABILITY_DEPENDENCY_HANDLING));
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
