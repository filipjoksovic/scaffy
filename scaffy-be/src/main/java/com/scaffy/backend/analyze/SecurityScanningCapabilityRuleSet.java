package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(27)
public class SecurityScanningCapabilityRuleSet implements CapabilityRuleSet {

	private static final double SAST_WEIGHT = 0.25;
	private static final double DEPENDENCY_WEIGHT = 0.20;
	private static final double SECRET_WEIGHT = 0.15;
	private static final double CONTAINER_IAC_WEIGHT = 0.15;
	private static final double REPORT_WEIGHT = 0.10;
	private static final double AUTOMATIC_TRIGGER_WEIGHT = 0.15;

	private static final String DIMENSION = "security_scanning";
	private static final String PRACTICE_SAST_DETECTED = "SAST or static security scanning detected";
	private static final String PRACTICE_DEPENDENCY_DETECTED = "Dependency or SCA scanning detected";
	private static final String PRACTICE_SECRET_DETECTED = "Secret scanning detected";
	private static final String PRACTICE_CONTAINER_IAC_DETECTED = "Container image or IaC scanning detected";
	private static final String PRACTICE_REPORT_DETECTED = "Security report or artifact detected";
	private static final String PRACTICE_AUTOMATIC_TRIGGER_DETECTED = "Automatic security scanning trigger detected";

	private static final String MISSING_SAST = "No SAST or static security scanning detected";
	private static final String MISSING_DEPENDENCY = "No dependency or SCA scanning detected";
	private static final String MISSING_SECRET = "No secret scanning detected";
	private static final String MISSING_CONTAINER_IAC = "No container image or IaC scanning detected";
	private static final String MISSING_REPORT = "No security report or artifact detected";
	private static final String MISSING_AUTOMATIC_TRIGGER = "No automatic security scanning trigger detected";

	private static final List<CommandRule> SAST_RULES = List.of(
			CommandRule.of("Semgrep", "(?:^|[;&|\\n]\\s*)(?<cmd>semgrep\\b[^\\n;&|]*)"),
			CommandRule.of("Sonar", "(?:^|[;&|\\n]\\s*)(?<cmd>sonar-scanner\\b[^\\n;&|]*)"),
			CommandRule.of("Bandit", "(?:^|[;&|\\n]\\s*)(?<cmd>bandit\\b[^\\n;&|]*)"),
			CommandRule.of("Brakeman", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:bundle\\s+exec\\s+)?brakeman\\b[^\\n;&|]*)"));

	private static final List<CommandRule> DEPENDENCY_RULES = List.of(
			CommandRule.of("npm", "(?:^|[;&|\\n]\\s*)(?<cmd>npm\\s+audit\\b[^\\n;&|]*)"),
			CommandRule.of("Yarn", "(?:^|[;&|\\n]\\s*)(?<cmd>yarn\\s+audit\\b[^\\n;&|]*)"),
			CommandRule.of("pnpm", "(?:^|[;&|\\n]\\s*)(?<cmd>pnpm\\s+audit\\b[^\\n;&|]*)"),
			CommandRule.of("OWASP Dependency Check", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:\\./)?mvnw?\\b[^\\n;&|]*org\\.owasp:dependency-check-maven[^\\n;&|]*)"),
			CommandRule.of("OWASP Dependency Check", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:gradle|\\./gradlew)\\b[^\\n;&|]*dependencyCheckAnalyze\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>pip-audit\\b[^\\n;&|]*)"),
			CommandRule.of("Python", "(?:^|[;&|\\n]\\s*)(?<cmd>safety\\s+check\\b[^\\n;&|]*)"),
			CommandRule.of("Ruby", "(?:^|[;&|\\n]\\s*)(?<cmd>(?:bundle\\s+exec\\s+)?bundler-audit\\b[^\\n;&|]*)"),
			CommandRule.of(".NET", "(?:^|[;&|\\n]\\s*)(?<cmd>dotnet\\s+list\\s+package\\s+--vulnerable\\b[^\\n;&|]*)"));

	private static final List<CommandRule> SECRET_RULES = List.of(
			CommandRule.of("Gitleaks", "(?:^|[;&|\\n]\\s*)(?<cmd>gitleaks\\b[^\\n;&|]*)"),
			CommandRule.of("TruffleHog", "(?:^|[;&|\\n]\\s*)(?<cmd>trufflehog\\b[^\\n;&|]*)"),
			CommandRule.of("detect-secrets", "(?:^|[;&|\\n]\\s*)(?<cmd>detect-secrets\\b[^\\n;&|]*)"));

	private static final List<CommandRule> CONTAINER_IAC_RULES = List.of(
			CommandRule.of("Trivy", "(?:^|[;&|\\n]\\s*)(?<cmd>trivy\\s+(?:image|fs|config)\\b[^\\n;&|]*)"),
			CommandRule.of("Grype", "(?:^|[;&|\\n]\\s*)(?<cmd>grype\\b[^\\n;&|]*)"),
			CommandRule.of("Snyk", "(?:^|[;&|\\n]\\s*)(?<cmd>snyk\\s+container\\b[^\\n;&|]*)"),
			CommandRule.of("Checkov", "(?:^|[;&|\\n]\\s*)(?<cmd>checkov\\b[^\\n;&|]*)"),
			CommandRule.of("tfsec", "(?:^|[;&|\\n]\\s*)(?<cmd>tfsec\\b[^\\n;&|]*)"),
			CommandRule.of("Terrascan", "(?:^|[;&|\\n]\\s*)(?<cmd>terrascan\\b[^\\n;&|]*)"),
			CommandRule.of("KICS", "(?:^|[;&|\\n]\\s*)(?<cmd>kics\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return DIMENSION;
	}

	@Override
	public DimensionAnalysis analyze(PipelineDocument document) {
		List<CommandMatch> sastMatches = securityContext(CommandMatcher.findMatches(document, SAST_RULES));
		List<CommandMatch> dependencyMatches = CommandMatcher.findMatches(document, DEPENDENCY_RULES);
		List<CommandMatch> secretMatches = CommandMatcher.findMatches(document, SECRET_RULES);
		List<CommandMatch> containerIacMatches = CommandMatcher.findMatches(document, CONTAINER_IAC_RULES);
		List<DetectedPractice> actionMatches = actionMatches(document);
		List<DetectedPractice> reportOutputs = reportOutputs(document, AnalysisSupport.distinct(sastMatches, dependencyMatches, secretMatches, containerIacMatches));

		if (sastMatches.isEmpty()
				&& dependencyMatches.isEmpty()
				&& secretMatches.isEmpty()
				&& containerIacMatches.isEmpty()
				&& actionMatches.isEmpty()
				&& reportOutputs.isEmpty()) {
			return missingAnalysis();
		}

		double score = 0.0;
		List<DetectedPractice> detected = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		Optional<DetectedPractice> sast = sast(sastMatches, actionMatches, reportOutputs);
		if (sast.isPresent()) {
			score += SAST_WEIGHT;
			detected.add(sast.get());
		}
		else {
			missing.add(MISSING_SAST);
		}

		Optional<DetectedPractice> dependency = dependency(dependencyMatches, actionMatches, reportOutputs);
		if (dependency.isPresent()) {
			score += DEPENDENCY_WEIGHT;
			detected.add(dependency.get());
		}
		else {
			missing.add(MISSING_DEPENDENCY);
		}

		Optional<DetectedPractice> secret = secret(secretMatches, reportOutputs);
		if (secret.isPresent()) {
			score += SECRET_WEIGHT;
			detected.add(secret.get());
		}
		else {
			missing.add(MISSING_SECRET);
		}

		Optional<DetectedPractice> containerIac = containerIac(containerIacMatches, reportOutputs, actionMatches);
		if (containerIac.isPresent()) {
			score += CONTAINER_IAC_WEIGHT;
			detected.add(containerIac.get());
		}
		else {
			missing.add(MISSING_CONTAINER_IAC);
		}

		Optional<DetectedPractice> report = report(reportOutputs, actionMatches);
		if (report.isPresent()) {
			score += REPORT_WEIGHT;
			detected.add(report.get());
		}
		else {
			missing.add(MISSING_REPORT);
		}

		Optional<DetectedPractice> automaticTrigger = automaticTrigger(document, AnalysisSupport.distinct(sastMatches, dependencyMatches, secretMatches, containerIacMatches), reportOutputs);
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
				confidence(automaticTrigger.isPresent(), report.isPresent()),
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
						MISSING_SAST,
						MISSING_DEPENDENCY,
						MISSING_SECRET,
						MISSING_CONTAINER_IAC,
						MISSING_REPORT,
						MISSING_AUTOMATIC_TRIGGER));
	}

	private Optional<DetectedPractice> sast(
			List<CommandMatch> sastMatches,
			List<DetectedPractice> actionMatches,
			List<DetectedPractice> reportOutputs) {
		if (!sastMatches.isEmpty()) {
			CommandMatch match = sastMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_SAST_DETECTED, match.evidence(), match.location()));
		}
		return actionMatches.stream()
				.filter(action -> AnalysisSupport.containsAny(AnalysisSupport.lower(action.evidence()), "github/codeql-action", "semgrep"))
				.findFirst()
				.or(() -> reportOutputs.stream()
						.filter(output -> AnalysisSupport.containsAny(AnalysisSupport.lower(output.evidence()), "sast"))
						.findFirst())
				.map(practice -> new DetectedPractice(PRACTICE_SAST_DETECTED, practice.evidence(), practice.location()));
	}

	private Optional<DetectedPractice> dependency(
			List<CommandMatch> dependencyMatches,
			List<DetectedPractice> actionMatches,
			List<DetectedPractice> reportOutputs) {
		if (!dependencyMatches.isEmpty()) {
			CommandMatch match = dependencyMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_DEPENDENCY_DETECTED, match.evidence(), match.location()));
		}
		return actionMatches.stream()
				.filter(action -> AnalysisSupport.containsAny(AnalysisSupport.lower(action.evidence()), "dependency-review-action", "snyk/actions"))
				.findFirst()
				.or(() -> reportOutputs.stream()
						.filter(output -> AnalysisSupport.containsAny(AnalysisSupport.lower(output.evidence()), "dependency_scanning"))
						.findFirst())
				.map(practice -> new DetectedPractice(PRACTICE_DEPENDENCY_DETECTED, practice.evidence(), practice.location()));
	}

	private Optional<DetectedPractice> secret(List<CommandMatch> secretMatches, List<DetectedPractice> reportOutputs) {
		if (!secretMatches.isEmpty()) {
			CommandMatch match = secretMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_SECRET_DETECTED, match.evidence(), match.location()));
		}
		return reportOutputs.stream()
				.filter(output -> AnalysisSupport.lower(output.evidence()).contains("secret_detection"))
				.findFirst()
				.map(output -> new DetectedPractice(PRACTICE_SECRET_DETECTED, output.evidence(), output.location()));
	}

	private Optional<DetectedPractice> containerIac(
			List<CommandMatch> containerIacMatches,
			List<DetectedPractice> reportOutputs,
			List<DetectedPractice> actionMatches) {
		if (!containerIacMatches.isEmpty()) {
			CommandMatch match = containerIacMatches.getFirst();
			return Optional.of(new DetectedPractice(PRACTICE_CONTAINER_IAC_DETECTED, match.evidence(), match.location()));
		}
		return actionMatches.stream()
				.filter(action -> AnalysisSupport.containsAny(AnalysisSupport.lower(action.evidence()), "trivy-action", "anchore/scan-action", "snyk/actions/docker"))
				.findFirst()
				.or(() -> reportOutputs.stream()
						.filter(output -> AnalysisSupport.lower(output.evidence()).contains("container_scanning"))
						.findFirst())
				.map(output -> new DetectedPractice(PRACTICE_CONTAINER_IAC_DETECTED, output.evidence(), output.location()));
	}

	private Optional<DetectedPractice> report(List<DetectedPractice> reportOutputs, List<DetectedPractice> actionMatches) {
		return reportOutputs.stream()
				.findFirst()
				.or(() -> actionMatches.stream()
						.filter(action -> AnalysisSupport.lower(action.evidence()).contains("upload-sarif"))
						.findFirst()
						.map(action -> new DetectedPractice(PRACTICE_REPORT_DETECTED, action.evidence(), action.location())));
	}

	private List<CommandMatch> securityContext(List<CommandMatch> matches) {
		List<CommandMatch> filtered = new ArrayList<>();
		for (CommandMatch match : matches) {
			String evidence = AnalysisSupport.lower(match.evidence());
			if ((!evidence.contains("semgrep") && !evidence.contains("sonar-scanner")) || securitySpecificContext(match)) {
				filtered.add(match);
			}
		}
		return filtered;
	}

	private boolean securitySpecificContext(CommandMatch match) {
		String context = AnalysisSupport.lower(AnalysisSupport.context(match.job(), match.step()));
		return AnalysisSupport.containsAny(context, "security", "sast", "vulnerability", "vulnerable", "secret", "dependency", "scan");
	}

	private List<DetectedPractice> actionMatches(PipelineDocument document) {
		List<DetectedPractice> matches = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				String uses = AnalysisSupport.lower(step.uses());
				String context = AnalysisSupport.lower(AnalysisSupport.context(job, step));
				if (uses.startsWith("github/codeql-action/upload-sarif")) {
					matches.add(new DetectedPractice(PRACTICE_REPORT_DETECTED, step.uses(), step.location()));
				}
				else if (uses.startsWith("github/codeql-action")) {
					matches.add(new DetectedPractice(PRACTICE_SAST_DETECTED, step.uses(), step.location()));
				}
				else if (uses.startsWith("github/dependency-review-action")) {
					matches.add(new DetectedPractice(PRACTICE_DEPENDENCY_DETECTED, step.uses(), step.location()));
				}
				else if (uses.startsWith("aquasecurity/trivy-action")
						|| uses.startsWith("anchore/scan-action")
						|| uses.startsWith("snyk/actions/docker")) {
					matches.add(new DetectedPractice(PRACTICE_CONTAINER_IAC_DETECTED, step.uses(), step.location()));
				}
				else if (uses.startsWith("snyk/actions")
						|| uses.startsWith("ossf/scorecard-action")) {
					matches.add(new DetectedPractice(PRACTICE_DEPENDENCY_DETECTED, step.uses(), step.location()));
				}
				else if (uses.contains("semgrep") && AnalysisSupport.containsAny(context, "security", "sast", "vulnerability", "secret", "scan")) {
					matches.add(new DetectedPractice(PRACTICE_SAST_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return matches;
	}

	private List<DetectedPractice> reportOutputs(PipelineDocument document, List<CommandMatch> securityMatches) {
		List<DetectedPractice> outputs = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineOutput output : job.outputs()) {
				if (securityReport(output) || securityJob(job) && securityMatches.stream().anyMatch(match -> match.job().equals(job))) {
					outputs.add(new DetectedPractice(PRACTICE_REPORT_DETECTED, output.evidence(), output.location()));
				}
			}
			for (PipelineStep step : job.steps()) {
				if (securitySarifUpload(step) || securityArtifactUpload(job, step)) {
					outputs.add(new DetectedPractice(PRACTICE_REPORT_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return outputs;
	}

	private boolean securityReport(PipelineOutput output) {
		return containsAny(
				AnalysisSupport.lower(output.type() + " " + output.evidence()),
				"sast",
				"dependency_scanning",
				"container_scanning",
				"secret_detection");
	}

	private boolean securityJob(PipelineJob job) {
		return AnalysisSupport.containsAny(AnalysisSupport.lower(job.id() + " " + job.name() + " " + job.stage()), "security", "sast", "vulnerability", "secret", "scan");
	}

	private boolean securitySarifUpload(PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("github/codeql-action/upload-sarif");
	}

	private boolean securityArtifactUpload(PipelineJob job, PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("actions/upload-artifact") && securityJob(job);
	}

	private Optional<DetectedPractice> automaticTrigger(
			PipelineDocument document,
			List<CommandMatch> commandMatches,
			List<DetectedPractice> reportOutputs) {
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			return document.triggers().stream()
					.filter(PipelineTrigger::automatic)
					.findFirst()
					.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()));
		}

		Optional<PipelineJob> automaticJob = automaticGitLabJob(document, commandMatches, reportOutputs);
		if (automaticJob.isEmpty()) {
			return Optional.empty();
		}

		return document.triggers().stream()
				.filter(PipelineTrigger::automatic)
				.findFirst()
				.map(trigger -> new DetectedPractice(PRACTICE_AUTOMATIC_TRIGGER_DETECTED, trigger.name(), trigger.location()))
				.or(() -> automaticJob.map(job -> new DetectedPractice(
						PRACTICE_AUTOMATIC_TRIGGER_DETECTED,
						"non-manual GitLab CI security scanning job",
						job.location())));
	}

	private Optional<PipelineJob> automaticGitLabJob(
			PipelineDocument document,
			List<CommandMatch> commandMatches,
			List<DetectedPractice> reportOutputs) {
		for (CommandMatch match : commandMatches) {
			if (!match.job().manualOnly()) {
				return Optional.of(match.job());
			}
		}
		for (DetectedPractice output : reportOutputs) {
			for (PipelineJob job : document.jobs()) {
				if (!job.manualOnly() && output.location().startsWith(job.location())) {
					return Optional.of(job);
				}
			}
		}
		return Optional.empty();
	}

	private Confidence confidence(boolean automaticTriggerDetected, boolean reportDetected) {
		if (automaticTriggerDetected || reportDetected) {
			return Confidence.HIGH;
		}
		return Confidence.MEDIUM;
	}
}
