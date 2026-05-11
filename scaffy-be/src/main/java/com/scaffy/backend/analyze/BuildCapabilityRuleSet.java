package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BuildCapabilityRuleSet implements CapabilityRuleSet {

	private static final double BUILD_COMMAND_WEIGHT = 0.35;
	private static final double DEPENDENCY_INSTALL_WEIGHT = 0.20;
	private static final double ECOSYSTEM_WEIGHT = 0.15;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;
	private static final double BUILD_OUTPUT_WEIGHT = 0.15;

	private static final List<CommandRule> BUILD_RULES = List.of(
			CommandRule.build("Generic build", "Generic", "(?:^|[;&|\\n]\\s*)(?<cmd>build)(?=\\s*$|\\s*[;&|])"),
			CommandRule.build("Node.js", "Node.js", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+build)(?=$|\\s|[;&|])"),
			CommandRule.build("Node.js", "Node.js", "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.build("Node.js", "Node.js", "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:run\\s+)?build)(?=$|\\s|[;&|])"),
			CommandRule.build("Java", "Maven", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\bpackage\\b|\\binstall\\b|\\bverify\\b))"),
			CommandRule.build("Java", "Gradle", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bbuild\\b)"),
			CommandRule.build(".NET", ".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+(?:build|publish)\\b[^\\n;&|]*)"),
			CommandRule.build("Go", "Go", "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+build\\b[^\\n;&|]*)"),
			CommandRule.build("Docker", "Docker", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+(?:buildx\\s+build|build)\\b[^\\n;&|]*)"),
			CommandRule.build("Python", "Python", "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+build\\b[^\\n;&|]*)"));

	private static final List<CommandRule> DEPENDENCY_RULES = List.of(
			CommandRule.dependency("Node.js", "npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+ci\\b[^\\n;&|]*)"),
			CommandRule.dependency("Node.js", "npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.dependency("Node.js", "pnpm", "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+install\\b[^\\n;&|]*)"),
			CommandRule.dependency("Node.js", "yarn", "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+install\\b[^\\n;&|]*)"),
			CommandRule.dependency(".NET", ".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+restore\\b[^\\n;&|]*)"),
			CommandRule.dependency("Java", "Maven", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdependency:go-offline\\b[^\\n;&|]*)"));

	private static final List<CommandRule> PUBLISH_RULES = List.of(
			CommandRule.output("Package publish", "npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+publish\\b[^\\n;&|]*)"),
			CommandRule.output("Package publish", "Maven", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*\\bdeploy\\b[^\\n;&|]*)"),
			CommandRule.output("Package publish", "Gradle", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*\\bpublish\\b[^\\n;&|]*)"),
			CommandRule.output("Package publish", ".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+nuget\\s+push\\b[^\\n;&|]*)"),
			CommandRule.output("Package publish", "Python", "(?:^|[;&|\\n]\\s*)(?<cmd>twine\\s+upload\\b[^\\n;&|]*)"),
			CommandRule.output("Docker image publish", "Docker", "(?:^|[;&|\\n]\\s*)(?<cmd>docker\\s+push\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return "build";
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> buildMatches = findMatches(document, BUILD_RULES);
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

		List<CommandMatch> dependencyMatches = findMatches(document, DEPENDENCY_RULES);
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

	private List<CommandMatch> findMatches(PipelineDocument document, List<CommandRule> rules) {
		List<CommandMatch> matches = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (step.command() == null || step.command().isBlank()) {
					continue;
				}
				for (CommandRule rule : rules) {
					Matcher matcher = rule.pattern().matcher(step.command());
					while (matcher.find()) {
						String evidence = matcher.group("cmd").trim();
						matches.add(new CommandMatch(job, step, evidence, step.location(), matcher.start("cmd"), rule));
					}
				}
			}
		}
		return matches;
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

	private Optional<DetectedPractice> buildOutput(PipelineDocument document, List<CommandMatch> buildMatches) {
		Optional<CommandMatch> dockerBuild = buildMatches.stream()
				.filter(match -> "Docker".equals(match.rule().ecosystem()))
				.findFirst();
		if (dockerBuild.isPresent()) {
			CommandMatch match = dockerBuild.get();
			return Optional.of(new DetectedPractice("Build output detected", match.evidence(), match.location()));
		}

		for (CommandMatch buildMatch : buildMatches) {
			for (PipelineOutput output : buildMatch.job().outputs()) {
				return Optional.of(new DetectedPractice("Build output detected", output.evidence(), output.location()));
			}
			for (PipelineStep step : buildMatch.job().steps()) {
				if (step.uses() != null && step.uses().toLowerCase(Locale.ROOT).startsWith("actions/upload-artifact")) {
					return Optional.of(new DetectedPractice("Build output detected", step.uses(), step.location()));
				}
			}
		}

		List<CommandMatch> publishMatches = findMatches(document, PUBLISH_RULES);
		for (CommandMatch publishMatch : publishMatches) {
			boolean sameBuildJob = buildMatches.stream().anyMatch(buildMatch -> buildMatch.job().equals(publishMatch.job()));
			if (sameBuildJob) {
				return Optional.of(new DetectedPractice("Build output detected", publishMatch.evidence(), publishMatch.location()));
			}
		}

		return Optional.empty();
	}

	private boolean deterministicInstall(String evidence) {
		String normalized = evidence.toLowerCase(Locale.ROOT);
		return normalized.matches(".*\\bnpm\\s+ci\\b.*")
				|| normalized.matches(".*\\bpnpm\\s+install\\b.*--frozen-lockfile.*")
				|| normalized.matches(".*\\byarn\\s+install\\b.*--immutable.*");
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

	private record CommandRule(Pattern pattern, String ecosystem) {

		private static CommandRule build(String label, String ecosystem, String regex) {
			return new CommandRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), ecosystem);
		}

		private static CommandRule dependency(String label, String ecosystem, String regex) {
			return new CommandRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), ecosystem);
		}

		private static CommandRule output(String label, String ecosystem, String regex) {
			return new CommandRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), ecosystem);
		}
	}

	private record CommandMatch(
			PipelineJob job,
			PipelineStep step,
			String evidence,
			String location,
			int position,
			CommandRule rule) {
	}
}
