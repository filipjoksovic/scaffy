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

	private static final String CAPABILITY_TEST_PRESENCE = "Test presence";
	private static final String CAPABILITY_CI_INTEGRATED_TESTS = "CI-integrated tests";
	private static final String CAPABILITY_REPORTS_COVERAGE = "Reports & coverage";
	private static final String CAPABILITY_MULTI_LAYER = "Multi-layer testing";

	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String ECOSYSTEM_RUBY = "Ruby";
	private static final String TOOL_MAVEN = "Maven";
	private static final String TOOL_GRADLE = "Gradle";

	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic test trigger detected";
	private static final String PRACTICE_TEST_OUTPUT_DETECTED = "Test report or coverage output detected";

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
		return "testing_maturity";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		List<CommandMatch> testMatches = CommandMatcher.findMatches(document, TEST_RULES);
		if (testMatches.isEmpty()) {
			findings.add(CapabilityFinding.missing("PIPELINE_MISSING_TEST_STAGE", dimension(), CAPABILITY_TEST_PRESENCE));
			findings.add(CapabilityFinding.missing("TESTS_NOT_AUTOMATED", dimension(), CAPABILITY_CI_INTEGRATED_TESTS));
			findings.add(CapabilityFinding.missing("NO_TEST_REPORT_OUTPUT", dimension(), CAPABILITY_REPORTS_COVERAGE));
			return findings;
		}

		CommandMatch primaryTest = testMatches.getFirst();
		findings.add(CapabilityFinding.positive("TESTS_PRESENT", dimension(), CAPABILITY_TEST_PRESENCE,
				primaryTest.evidence(), primaryTest.location()));

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, testMatches);
		if (automaticTrigger.isPresent()) {
			findings.add(CapabilityFinding.positive("CI_INTEGRATED_TESTS", dimension(), CAPABILITY_CI_INTEGRATED_TESTS,
					automaticTrigger.get().evidence(), automaticTrigger.get().location()));
		}
		else {
			findings.add(CapabilityFinding.smell("TESTS_NOT_AUTOMATED", dimension(), CAPABILITY_CI_INTEGRATED_TESTS,
					primaryTest.evidence(), primaryTest.location()));
		}

		List<CommandMatch> reportMatches = CommandMatcher.findMatches(document, REPORT_RULES);
		Optional<DetectedPractice> testOutput = testOutput(testMatches, reportMatches);
		if (testOutput.isPresent()) {
			findings.add(CapabilityFinding.positive("TEST_REPORT_OUTPUT_PRESENT", dimension(), CAPABILITY_REPORTS_COVERAGE,
					testOutput.get().evidence(), testOutput.get().location()));
			boolean hasCoverage = reportMatches.stream()
					.filter(m -> sameTestJob(m, testMatches))
					.anyMatch(m -> "Coverage".equals(m.rule().ecosystem()) || "Go coverage".equals(m.rule().ecosystem()));
			if (hasCoverage) {
				findings.add(CapabilityFinding.positive("COVERAGE_TOOL_PRESENT", dimension(), CAPABILITY_REPORTS_COVERAGE,
						testOutput.get().evidence(), testOutput.get().location()));
			}
		}
		else {
			findings.add(CapabilityFinding.missing("NO_TEST_REPORT_OUTPUT", dimension(), CAPABILITY_REPORTS_COVERAGE));
		}

		Set<String> layers = testLayers(testMatches);
		if (layers.size() >= 2) {
			findings.add(CapabilityFinding.positive("MULTI_LAYER_TEST_SIGNAL", dimension(), CAPABILITY_MULTI_LAYER,
					String.join(", ", layers), primaryTest.job().location()));
		}

		return findings;
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

}
