package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(25)
public class CodeAnalysisCapabilityRuleSet implements CapabilityRuleSet {

	private static final String CAPABILITY_LINT_STATIC = "Lint / static analysis";
	private static final String CAPABILITY_FORMATTING = "Formatting";
	private static final String CAPABILITY_TYPE_CHECKING = "Type checking";

	private static final String ECOSYSTEM_NODE_JS = "Node.js";
	private static final String ECOSYSTEM_JAVA = "Java";
	private static final String ECOSYSTEM_DOTNET = ".NET";
	private static final String ECOSYSTEM_PYTHON = "Python";
	private static final String ECOSYSTEM_GO = "Go";
	private static final String ECOSYSTEM_GENERAL = "General";

	private static final String PRACTICE_LINT_STATIC_DETECTED = "Lint or static analysis command detected";
	private static final String PRACTICE_FORMATTER_DETECTED = "Formatter or style check detected";
	private static final String PRACTICE_TYPE_DEEP_ANALYSIS_DETECTED = "Type checking or deeper static analysis detected";

	private static final String KEYWORD_ANALYSIS = "analysis";
	private static final String KEYWORD_QUALITY = "quality";

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
		return "workflow_quality";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CapabilityFinding> findings = new ArrayList<>();

		List<CommandMatch> codeAnalysisMatches = codeAnalysisMatches(document);
		List<CommandMatch> formatterMatches = CommandMatcher.findMatches(document, FORMATTER_RULES);
		List<CommandMatch> typeDeepMatches = codeAnalysisContext(CommandMatcher.findMatches(document, TYPE_DEEP_ANALYSIS_RULES));
		List<DetectedPractice> actionMatches = actionMatches(document);

		// Lint / static analysis
		Optional<DetectedPractice> lintStatic = lintStaticPractice(codeAnalysisMatches, actionMatches);
		if (lintStatic.isPresent()) {
			findings.add(CapabilityFinding.positive("LINT_STATIC_PRESENT", dimension(), CAPABILITY_LINT_STATIC,
					lintStatic.get().evidence(), lintStatic.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("LINT_STATIC_MISSING", dimension(), CAPABILITY_LINT_STATIC));
		}

		// Formatting
		if (!formatterMatches.isEmpty()) {
			CommandMatch formatter = formatterMatches.getFirst();
			findings.add(CapabilityFinding.positive("FORMATTER_PRESENT", dimension(), CAPABILITY_FORMATTING,
					formatter.evidence(), formatter.location()));
		}
		else {
			findings.add(CapabilityFinding.missing("FORMATTER_MISSING", dimension(), CAPABILITY_FORMATTING));
		}

		// Type checking
		Optional<DetectedPractice> typeDeep = typeDeepPractice(typeDeepMatches, actionMatches);
		if (typeDeep.isPresent()) {
			findings.add(CapabilityFinding.positive("TYPE_CHECK_PRESENT", dimension(), CAPABILITY_TYPE_CHECKING,
					typeDeep.get().evidence(), typeDeep.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("TYPE_CHECK_MISSING", dimension(), CAPABILITY_TYPE_CHECKING));
		}

		return findings;
	}

	private List<CommandMatch> codeAnalysisMatches(PipelineDocument document) {
		return codeAnalysisContext(CommandMatcher.findMatches(document, CODE_ANALYSIS_RULES));
	}

	private List<CommandMatch> codeAnalysisContext(List<CommandMatch> matches) {
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
		return AnalysisSupport.containsAny(context, "lint", "static", KEYWORD_QUALITY, KEYWORD_ANALYSIS, "analyze", "semgrep");
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


}

