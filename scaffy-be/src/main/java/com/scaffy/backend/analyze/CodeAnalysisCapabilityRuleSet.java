package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(25)
public class CodeAnalysisCapabilityRuleSet implements CapabilityRuleSet {

	private static final double LINT_STATIC_WEIGHT = 0.30;
	private static final double FORMATTER_WEIGHT = 0.20;
	private static final double TYPE_DEEP_ANALYSIS_WEIGHT = 0.20;
	private static final double REPORT_OUTPUT_WEIGHT = 0.15;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;

	private static final String DIMENSION = "code_analysis";
	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_JAVA = "Java";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_GENERAL = "General";

	private static final String PRACTICE_LINT_STATIC_DETECTED = "Lint or static analysis command detected";
	private static final String PRACTICE_FORMATTER_DETECTED = "Formatter or style check detected";
	private static final String PRACTICE_TYPE_DEEP_ANALYSIS_DETECTED = "Type checking or deeper static analysis detected";
	private static final String PRACTICE_REPORT_OUTPUT_DETECTED = "Code quality report or artifact detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic code analysis trigger detected";

	private static final String MISSING_LINT_STATIC = "No lint or static analysis command detected";
	private static final String MISSING_FORMATTER = "No formatter or style check detected";
	private static final String MISSING_TYPE_DEEP_ANALYSIS = "No type checking or deeper static analysis detected";
	private static final String MISSING_REPORT_OUTPUT = "No code quality report or artifact detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic code analysis trigger detected";

	private static final List<CommandRule> CODE_ANALYSIS_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+lint(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+(?:run\\s+)?lint(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:(?:--dir|-C|--filter)\\s+(?:\\$\\{\\{[^}]+}}|\\S+)\\s+)*(?:run\\s+)?lint(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+(?:run\\s+)?lint(?::[\\w-]+)?\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:npx\\s+)?eslint\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+typecheck\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:npx\\s+)?tsc\\s+--noEmit\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_JAVA, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\bcheckstyle:check\\b|\\bpmd:check\\b|\\bspotbugs:check\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_JAVA, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*(?:\\bcheck\\b|\\bcheckstyleMain\\b|\\bpmdMain\\b|\\bspotbugsMain\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+build\\b[^\\n;&|]*(?:/warnaserror|-warnaserror)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+sonarscanner\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>ruff\\s+check\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>flake8\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>pylint\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>mypy\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>go\\s+vet\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>golangci-lint\\s+run\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>staticcheck\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GENERAL, "(?:^|[;&|\\n]\\s*)(?<cmd>sonar-scanner\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GENERAL, "(?:^|[;&|\\n]\\s*)(?<cmd>semgrep\\b[^\\n;&|]*)"));

	private static final List<CommandRule> FORMATTER_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:npx\\s+)?prettier\\s+--check\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+format\\b[^\\n;&|]*(?:--verify-no-changes|--check)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>black\\s+--check\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>isort\\s+--check-only\\b[^\\n;&|]*)"));

	private static final List<CommandRule> TYPE_DEEP_ANALYSIS_RULES = List.of(
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+run\\s+typecheck\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_NODE_JS, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:npx\\s+)?tsc\\s+--noEmit\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_JAVA, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*(?:\\bpmd:check\\b|\\bspotbugs:check\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_JAVA, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*(?:\\bpmdMain\\b|\\bspotbugsMain\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_DOTNET, "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+(?:build\\b[^\\n;&|]*(?:/warnaserror|-warnaserror)|sonarscanner\\b)[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_PYTHON, "(?:^|[;&|\\n]\\s*)(?<cmd>mypy\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GO, "(?:^|[;&|\\n]\\s*)(?<cmd>(?:go\\s+vet|golangci-lint\\s+run|staticcheck)\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GENERAL, "(?:^|[;&|\\n]\\s*)(?<cmd>sonar-scanner\\b[^\\n;&|]*)"),
			CommandRule.of(ECOSYSTEM_GENERAL, "(?:^|[;&|\\n]\\s*)(?<cmd>semgrep\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> codeAnalysisMatches = codeAnalysisMatches(document);
		List<CommandMatch> formatterMatches = CommandMatcher.findMatches(document, FORMATTER_RULES);
		List<CommandMatch> typeDeepMatches = codeAnalysisContext(document, CommandMatcher.findMatches(document, TYPE_DEEP_ANALYSIS_RULES));
		List<DetectedPractice> actionMatches = actionMatches(document);
		List<CommandMatch> allAnalysisMatches = AnalysisSupport.distinct(codeAnalysisMatches, formatterMatches, typeDeepMatches);
		if (allAnalysisMatches.isEmpty() && actionMatches.isEmpty()) {
			return missingAnalysis();
		}

		double score = 0.0;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		Optional<DetectedPractice> lintStatic = lintStaticPractice(codeAnalysisMatches, actionMatches);
		if (lintStatic.isPresent()) {
			score += LINT_STATIC_WEIGHT;
			detected.add(lintStatic.get());
		}
		else {
			missing.add(MISSING_LINT_STATIC);
		}

		if (formatterMatches.isEmpty()) {
			missing.add(MISSING_FORMATTER);
		}
		else {
			CommandMatch formatter = formatterMatches.getFirst();
			score += FORMATTER_WEIGHT;
			detected.add(new DetectedPractice(PRACTICE_FORMATTER_DETECTED, formatter.evidence(), formatter.location()));
		}

		Optional<DetectedPractice> typeDeepAnalysis = typeDeepPractice(typeDeepMatches, actionMatches);
		if (typeDeepAnalysis.isPresent()) {
			score += TYPE_DEEP_ANALYSIS_WEIGHT;
			detected.add(typeDeepAnalysis.get());
		}
		else {
			missing.add(MISSING_TYPE_DEEP_ANALYSIS);
		}

		Optional<DetectedPractice> reportOutput = reportOutput(allAnalysisMatches, actionMatches);
		if (reportOutput.isPresent()) {
			score += REPORT_OUTPUT_WEIGHT;
			detected.add(reportOutput.get());
		}
		else {
			missing.add(MISSING_REPORT_OUTPUT);
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, allAnalysisMatches);
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
				confidence(automaticTrigger.isPresent(), reportOutput.isPresent()),
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
						MISSING_LINT_STATIC,
						MISSING_FORMATTER,
						MISSING_TYPE_DEEP_ANALYSIS,
						MISSING_REPORT_OUTPUT,
						MISSING_AUTOMATIC_TRIGGER));
	}

	private List<CommandMatch> codeAnalysisMatches(PipelineDocument document) {
		return codeAnalysisContext(document, CommandMatcher.findMatches(document, CODE_ANALYSIS_RULES));
	}

	private List<CommandMatch> codeAnalysisContext(PipelineDocument document, List<CommandMatch> matches) {
		List<CommandMatch> filtered = new ArrayList<>();
		for (CommandMatch match : matches) {
			if (!semgrep(match) || codeAnalysisSemgrepContext(match)) {
				filtered.add(match);
			}
		}
		return filtered;
	}

	private boolean semgrep(CommandMatch match) {
		return AnalysisSupport.lower(match.evidence()).contains("semgrep");
	}

	private boolean codeAnalysisSemgrepContext(CommandMatch match) {
		String context = AnalysisSupport.lower(match.evidence() + " " + match.job().id() + " " + match.job().name() + " " + match.job().stage());
		if (AnalysisSupport.containsAny(context, "sast", "security", "vulnerab", "secret", "dependency", "scan")) {
			return false;
		}
		return AnalysisSupport.containsAny(context, "lint", "static", "quality", "analysis", "analyze", "semgrep");
	}

	private Optional<DetectedPractice> lintStaticPractice(
			List<CommandMatch> codeAnalysisMatches,
			List<DetectedPractice> actionMatches) {
		if (!codeAnalysisMatches.isEmpty()) {
			CommandMatch match = codeAnalysisMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_LINT_STATIC_DETECTED, match.evidence(), match.location()));
		}
		return actionMatches.stream().findFirst()
				.map(action -> new DetectedPractice(PRACTICE_LINT_STATIC_DETECTED, action.evidence(), action.location()));
	}

	private Optional<DetectedPractice> typeDeepPractice(
			List<CommandMatch> typeDeepMatches,
			List<DetectedPractice> actionMatches) {
		if (!typeDeepMatches.isEmpty()) {
			CommandMatch match = typeDeepMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_TYPE_DEEP_ANALYSIS_DETECTED, match.evidence(), match.location()));
		}
		return actionMatches.stream()
				.filter(action -> deeperAction(action.evidence()))
				.findFirst()
				.map(action -> new DetectedPractice(PRACTICE_TYPE_DEEP_ANALYSIS_DETECTED, action.evidence(), action.location()));
	}

	private boolean deeperAction(String evidence) {
		String action = AnalysisSupport.lower(evidence);
		return action.contains("sonarsource/")
				|| action.contains("golangci/golangci-lint-action")
				|| action.contains("reviewdog/");
	}

	private List<DetectedPractice> actionMatches(PipelineDocument document) {
		List<DetectedPractice> matches = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				if (codeAnalysisAction(step)) {
					matches.add(new DetectedPractice(PRACTICE_LINT_STATIC_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return matches;
	}

	private boolean codeAnalysisAction(PipelineStep step) {
		if (step.uses() == null) {
			return false;
		}
		String uses = AnalysisSupport.lower(step.uses());
		return uses.startsWith("github/super-linter")
				|| uses.startsWith("super-linter/super-linter")
				|| uses.startsWith("sonarsource/sonarqube-scan-action")
				|| uses.startsWith("sonarsource/sonarcloud-github-action")
				|| uses.startsWith("golangci/golangci-lint-action")
				|| uses.startsWith("reviewdog/action-");
	}

	private Optional<DetectedPractice> reportOutput(
			List<CommandMatch> codeAnalysisMatches,
			List<DetectedPractice> actionMatches) {
		for (CommandMatch match : codeAnalysisMatches) {
			for (PipelineOutput output : match.job().outputs()) {
				if (codeQualityOutput(output) || codeQualityJob(match.job())) {
					return Optional.of(new DetectedPractice(PRACTICE_REPORT_OUTPUT_DETECTED, output.evidence(), output.location()));
				}
			}
			for (PipelineStep step : match.job().steps()) {
				if (codeQualityUploadAction(step)) {
					return Optional.of(new DetectedPractice(PRACTICE_REPORT_OUTPUT_DETECTED, step.uses(), step.location()));
				}
			}
		}
		for (DetectedPractice actionMatch : actionMatches) {
			if (AnalysisSupport.containsAny(AnalysisSupport.lower(actionMatch.evidence()), "sonar", "reviewdog")) {
				return Optional.of(new DetectedPractice(
						PRACTICE_REPORT_OUTPUT_DETECTED,
						actionMatch.evidence(),
						actionMatch.location()));
			}
		}
		return Optional.empty();
	}

	private boolean codeQualityOutput(PipelineOutput output) {
		return AnalysisSupport.containsAny(AnalysisSupport.lower(output.type() + " " + output.evidence()), "codequality", "code-quality", "code quality");
	}

	private boolean codeQualityJob(PipelineJob job) {
		return AnalysisSupport.containsAny(AnalysisSupport.lower(job.id() + " " + job.name() + " " + job.stage()), "quality", "lint", "static", "analysis");
	}

	private boolean codeQualityUploadAction(PipelineStep step) {
		if (step.uses() == null) {
			return false;
		}
		String uses = AnalysisSupport.lower(step.uses());
		return uses.startsWith("actions/upload-artifact") && AnalysisSupport.containsAny(AnalysisSupport.lower(step.location()), "quality", "lint", "analysis");
	}

	private Optional<DetectedPractice> automaticTrigger(PipelineDocument document, List<CommandMatch> codeAnalysisMatches) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		boolean automaticAnalysisJob = codeAnalysisMatches.stream().anyMatch(match -> !match.job().manualOnly());
		if (!automaticAnalysisJob) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> codeAnalysisMatches.stream()
						.filter(match -> !match.job().manualOnly())
						.findFirst()
						.map(match -> new DetectedPractice(
								PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
								"non-manual GitLab CI code analysis job",
								match.job().location())));
	}

	private Confidence confidence(boolean automaticTriggerDetected, boolean reportOutputDetected) {
		if (automaticTriggerDetected && reportOutputDetected) {
			return Confidence.HIGH;
		}
		return Confidence.MEDIUM;
	}

}
