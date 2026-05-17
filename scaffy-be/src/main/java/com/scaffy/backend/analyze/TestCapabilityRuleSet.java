package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class TestCapabilityRuleSet implements CapabilityRuleSet {

	private static final double TEST_COMMAND_WEIGHT = 0.35;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.20;
	private static final double ECOSYSTEM_WEIGHT = 0.15;
	private static final double TEST_OUTPUT_WEIGHT = 0.15;
	private static final double QUALITY_SIGNAL_WEIGHT = 0.15;

	private static final String DIMENSION = "test";
	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String ECOSYSTEM_RUBY = "Ruby";
	private static final String TOOL_MAVEN = "Maven";
	private static final String TOOL_GRADLE = "Gradle";

	private static final String PRACTICE_TEST_COMMAND_DETECTED = "Automated test command detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic test trigger detected";
	private static final String PRACTICE_TEST_ECOSYSTEM_DETECTED = "Test ecosystem detected";
	private static final String PRACTICE_TEST_OUTPUT_DETECTED = "Test report or coverage output detected";
	private static final String PRACTICE_QUALITY_SIGNAL_DETECTED = "Test quality signal detected";

	private static final String MISSING_TEST_COMMAND = "No automated test command detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic test trigger detected";
	private static final String MISSING_TEST_ECOSYSTEM = "No test ecosystem detected";
	private static final String MISSING_TEST_OUTPUT = "No test report or coverage output detected";
	private static final String MISSING_QUALITY_SIGNAL = "No test quality signal detected";

	private static final List<CommandRule> TEST_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+(?:run\\s+)?test(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+(?:run\\s+)?test(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:(?:--dir|-C|--filter)\\s+(?:\\$\\{\\{[^}]+}}|\\S+)\\s+)*(?:run\\s+)?test(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:run\\s+)?test(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npx\\s+(?:vitest|jest)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npx\\s+playwright\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npx\\s+cypress\\s+run\\b[^\\n;&|]*)"),
			CommandRule.of(TOOL_MAVEN, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\btest\\b|\\bverify\\b)[^\\n;&|]*)"),
			CommandRule.of(TOOL_GRADLE, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*(?:\\btest\\b|\\bcheck\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>pytest\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>python3?\\s+-m\\s+(?:pytest|unittest)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_RUBY, "(?:^|[;&|\\n]\\s*)(?<cmd>bundle\\s+exec\\s+rspec\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_RUBY, "(?:^|[;&|\\n]\\s*)(?<cmd>bundle\\s+exec\\s+rake\\s+test\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_RUBY, "(?:^|[;&|\\n]\\s*)(?<cmd>bin/rails\\s+test\\b[^\\n;&|]*)"));

	private static final List<CommandRule> REPORT_RULES = List.of(
			CommandRule.of("Coverage", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:--coverage|\\bcoverage\\b|\\bjacoco\\b|\\bnyc\\b)[^\\n;&|]*)"),
			CommandRule.of("JUnit report", "(?:^|[;&|\\n]\\s*)(?<cmd>[^\\n;&|]*(?:--junitxml|--logger|\\bjunit\\b)[^\\n;&|]*)"),
			CommandRule.of("Go coverage", "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+test\\b[^\\n;&|]*-cover[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> testMatches = CommandMatcher.findMatches(document, TEST_RULES);
		if (testMatches.isEmpty()) {
			return missingAnalysis();
		}

		double score = TEST_COMMAND_WEIGHT;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		CommandMatch primaryTest = testMatches.getFirst();
		detected.add(new DetectedPractice(PRACTICE_TEST_COMMAND_DETECTED, primaryTest.evidence(), primaryTest.location()));

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, testMatches);
		if (automaticTrigger.isPresent()) {
			score += AUTOMATIC_TRIGGER_WEIGHT;
			detected.add(automaticTrigger.get());
		}
		else {
			missing.add(MISSING_AUTOMATIC_TRIGGER);
		}

		Set<String> ecosystems = ecosystems(testMatches);
		if (ecosystems.isEmpty()) {
			missing.add(MISSING_TEST_ECOSYSTEM);
		}
		else {
			score += ECOSYSTEM_WEIGHT;
			detected.add(new DetectedPractice(PRACTICE_TEST_ECOSYSTEM_DETECTED, String.join(", ", ecosystems), primaryTest.location()));
		}

		List<CommandMatch> reportMatches = CommandMatcher.findMatches(document, REPORT_RULES);
		Optional<DetectedPractice> testOutput = testOutput(testMatches, reportMatches);
		if (testOutput.isPresent()) {
			score += TEST_OUTPUT_WEIGHT;
			detected.add(testOutput.get());
		}
		else {
			missing.add(MISSING_TEST_OUTPUT);
		}

		Optional<DetectedPractice> qualitySignal = qualitySignal(testMatches, reportMatches);
		if (qualitySignal.isPresent()) {
			score += QUALITY_SIGNAL_WEIGHT;
			detected.add(qualitySignal.get());
		}
		else {
			missing.add(MISSING_QUALITY_SIGNAL);
		}

		double roundedScore = round(score);
		return new DimensionAnalysis(
				dimension(),
				roundedScore,
				level(roundedScore),
				roundedScore >= 0.8 ? AnalysisStatus.COMPLETE : AnalysisStatus.PARTIAL,
				confidence(automaticTrigger.isPresent(), testOutput.isPresent()),
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
						MISSING_TEST_COMMAND,
						MISSING_AUTOMATIC_TRIGGER,
						MISSING_TEST_ECOSYSTEM,
						MISSING_TEST_OUTPUT,
						MISSING_QUALITY_SIGNAL));
	}

	private Set<String> ecosystems(List<CommandMatch> testMatches) {
		Set<String> ecosystems = new LinkedHashSet<>();
		for (CommandMatch testMatch : testMatches) {
			ecosystems.add(testMatch.rule().ecosystem());
		}
		return ecosystems;
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<CommandMatch> testMatches) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		boolean automaticTestJob = testMatches.stream().anyMatch(match -> !match.job().manualOnly());
		if (!automaticTestJob) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> testMatches.stream()
						.filter(match -> !match.job().manualOnly())
						.findFirst()
						.map(match -> new DetectedPractice(
								PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
								"non-manual GitLab CI test job",
								match.job().location())));
	}

	private Optional<DetectedPractice> testOutput(List<CommandMatch> testMatches, List<CommandMatch> reportMatches) {
		Optional<DetectedPractice> reportCommand = reportMatches.stream()
				.filter(report -> sameTestJob(report, testMatches))
				.findFirst()
				.map(report -> new DetectedPractice(PRACTICE_TEST_OUTPUT_DETECTED, report.evidence(), report.location()));
		if (reportCommand.isPresent()) {
			return reportCommand;
		}

		for (CommandMatch testMatch : testMatches) {
			for (PipelineStep step : testMatch.job().steps()) {
				if (testUploadAction(step)) {
					return Optional.of(new DetectedPractice(PRACTICE_TEST_OUTPUT_DETECTED, step.uses(), step.location()));
				}
			}
			if (!testMatch.job().outputs().isEmpty()) {
				PipelineOutput output = testMatch.job().outputs().getFirst();
				return Optional.of(new DetectedPractice(PRACTICE_TEST_OUTPUT_DETECTED, output.evidence(), output.location()));
			}
		}
		return Optional.empty();
	}

	private boolean testUploadAction(PipelineStep step) {
		if (step.uses() == null) {
			return false;
		}
		String uses = step.uses().toLowerCase(Locale.ROOT);
		return uses.startsWith("actions/upload-artifact")
				|| uses.contains("test-reporter")
				|| uses.contains("junit")
				|| uses.contains("publish-unit-test")
				|| uses.contains("codecov");
	}

	private Optional<DetectedPractice> qualitySignal(List<CommandMatch> testMatches, List<CommandMatch> reportMatches) {
		Optional<CommandMatch> report = reportMatches.stream()
				.filter(match -> sameTestJob(match, testMatches))
				.findFirst();
		if (report.isPresent()) {
			CommandMatch reportMatch = report.get();
			return Optional.of(new DetectedPractice(PRACTICE_QUALITY_SIGNAL_DETECTED, reportMatch.evidence(), reportMatch.location()));
		}

		Set<String> layers = testLayers(testMatches);
		if (layers.size() >= 2) {
			CommandMatch first = testMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_QUALITY_SIGNAL_DETECTED, String.join(", ", layers), first.job().location()));
		}

		return Optional.empty();
	}

	private Set<String> testLayers(List<CommandMatch> testMatches) {
		Set<String> layers = new LinkedHashSet<>();
		for (CommandMatch testMatch : testMatches) {
			String text = (
					testMatch.evidence() + " "
							+ testMatch.job().id() + " "
							+ testMatch.job().name() + " "
							+ testMatch.job().stage())
					.toLowerCase(Locale.ROOT);
			if (text.contains("unit")) {
				layers.add("unit");
			}
			if (text.contains("integration") || text.contains("it-test")) {
				layers.add("integration");
			}
			if (text.contains("e2e") || text.contains("playwright") || text.contains("cypress")) {
				layers.add("e2e");
			}
		}
		return layers;
	}

	private boolean sameTestJob(CommandMatch candidate, List<CommandMatch> testMatches) {
		return testMatches.stream().anyMatch(testMatch -> testMatch.job().equals(candidate.job()));
	}

	private Confidence confidence(boolean automaticTriggerDetected, boolean testOutputDetected) {
		if (automaticTriggerDetected && testOutputDetected) {
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
