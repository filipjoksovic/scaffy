package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(27)
public class SecurityScanningCapabilityRuleSet implements CapabilityRuleSet {

	private static final String CAPABILITY_STATIC_ANALYSIS = "Static analysis";
	private static final String CAPABILITY_DEP_CONTAINER_SCAN = "Dependency / container scanning";
	private static final String CAPABILITY_SECRET_HYGIENE = "Secret hygiene";
	private static final String CAPABILITY_SAFE_ACTION_TOKEN = "Safe action/token usage";
	private static final String CAPABILITY_POLICY_AS_CODE = "Policy as code";

	private static final String PRACTICE_SAST_DETECTED = "SAST or static security scanning detected";
	private static final String PRACTICE_DEPENDENCY_DETECTED = "Dependency or SCA scanning detected";
	private static final String PRACTICE_SECRET_DETECTED = "Secret scanning detected";
	private static final String PRACTICE_CONTAINER_IAC_DETECTED = "Container image or IaC scanning detected";
	private static final String PRACTICE_REPORT_DETECTED = "Security report or artifact detected";

	private static final String PRACTICE_HARDCODED_SECRET = "Potential hardcoded secret in env";
	private static final String PRACTICE_PERMISSIONS_DECLARED = "GitHub Actions permissions declared";
	private static final String PRACTICE_UNPINNED_ACTION = "Action not pinned to commit SHA";
	private static final String PRACTICE_POLICY_TOOL_DETECTED = "Policy-as-code tool detected";

	private static final String TOOL_SEMGREP = "semgrep";
	private static final String KEYWORD_VULNERABILITY = "vulnerability";
	private static final String KEYWORD_SECRET = "secret";
	private static final String KEYWORD_SECURITY = "security";

	private static final Pattern HARDCODED_SECRET_PATTERN = Pattern.compile(
			"(?i)(?:password|secret|token|api[_\\-]?key|private[_\\-]?key|access[_\\-]?key|authtoken|credentials?)[=:]\\s*(\\S+)");

	private static final Pattern PINNED_SHA_PATTERN = Pattern.compile("@[a-f0-9]{40}(?:[^a-zA-Z0-9]|$)");

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

	private static final List<CommandRule> POLICY_AS_CODE_RULES = List.of(
			CommandRule.of("Checkov", "(?:^|[;&|\\n]\\s*)(?<cmd>checkov\\b[^\\n;&|]*)"),
			CommandRule.of("OPA", "(?:^|[;&|\\n]\\s*)(?<cmd>opa\\s+(?:eval|run|test)\\b[^\\n;&|]*)"),
			CommandRule.of("Conftest", "(?:^|[;&|\\n]\\s*)(?<cmd>conftest\\b[^\\n;&|]*)"),
			CommandRule.of("tfsec", "(?:^|[;&|\\n]\\s*)(?<cmd>tfsec\\b[^\\n;&|]*)"),
			CommandRule.of("KICS", "(?:^|[;&|\\n]\\s*)(?<cmd>kics\\b[^\\n;&|]*)"),
			CommandRule.of("Terrascan", "(?:^|[;&|\\n]\\s*)(?<cmd>terrascan\\b[^\\n;&|]*)"));

	@Override
	public String dimension() {
		return "security_integration";
	}

	@Override
	public List<CapabilityFinding> detect(PipelineDocument document) {
		List<CommandMatch> sastMatches = securityContext(CommandMatcher.findMatches(document, SAST_RULES));
		List<CommandMatch> dependencyMatches = CommandMatcher.findMatches(document, DEPENDENCY_RULES);
		List<CommandMatch> secretMatches = CommandMatcher.findMatches(document, SECRET_RULES);
		List<CommandMatch> containerIacMatches = CommandMatcher.findMatches(document, CONTAINER_IAC_RULES);
		List<DetectedPractice> actionMatches = actionMatches(document);
		List<DetectedPractice> reportOutputs = reportOutputs(document, AnalysisSupport.distinct(sastMatches, dependencyMatches, secretMatches, containerIacMatches));

		List<CapabilityFinding> findings = new ArrayList<>();

		// Static analysis
		Optional<DetectedPractice> sast = sast(sastMatches, actionMatches, reportOutputs);
		if (sast.isPresent()) {
			findings.add(CapabilityFinding.positive("SAST_PRESENT", dimension(), CAPABILITY_STATIC_ANALYSIS,
					sast.get().evidence(), sast.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("SAST_MISSING", dimension(), CAPABILITY_STATIC_ANALYSIS));
		}

		// Dependency / container scanning
		Optional<DetectedPractice> dependency = dependency(dependencyMatches, actionMatches, reportOutputs);
		if (dependency.isPresent()) {
			findings.add(CapabilityFinding.positive("DEPENDENCY_SCAN_PRESENT", dimension(), CAPABILITY_DEP_CONTAINER_SCAN,
					dependency.get().evidence(), dependency.get().location()));
		}

		Optional<DetectedPractice> containerIac = containerIac(containerIacMatches, reportOutputs, actionMatches);
		if (containerIac.isPresent()) {
			findings.add(CapabilityFinding.positive("CONTAINER_SCAN_PRESENT", dimension(), CAPABILITY_DEP_CONTAINER_SCAN,
					containerIac.get().evidence(), containerIac.get().location()));
		}

		if (dependency.isEmpty() && containerIac.isEmpty()) {
			findings.add(CapabilityFinding.missing("DEPENDENCY_SCAN_MISSING", dimension(), CAPABILITY_DEP_CONTAINER_SCAN));
		}

		// Secret hygiene
		Optional<DetectedPractice> secret = secret(secretMatches, reportOutputs);
		if (secret.isPresent()) {
			findings.add(CapabilityFinding.positive("SECRET_SCAN_PRESENT", dimension(), CAPABILITY_SECRET_HYGIENE,
					secret.get().evidence(), secret.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("SECRET_SCAN_MISSING", dimension(), CAPABILITY_SECRET_HYGIENE));
		}

		Optional<DetectedPractice> hardcoded = hardcodedSecretSignal(document);
		if (hardcoded.isPresent()) {
			findings.add(CapabilityFinding.smell("HARDCODED_SECRET_IN_ENV", dimension(), CAPABILITY_SECRET_HYGIENE,
					hardcoded.get().evidence(), hardcoded.get().location()));
		}

		// Safe action / token usage (GitHub Actions only)
		if (document.provider() == PipelineProvider.GITHUB_ACTIONS) {
			detectSafeActionTokenUsage(findings, document);
		}

		// Policy as code
		List<CommandMatch> policyMatches = CommandMatcher.findMatches(document, POLICY_AS_CODE_RULES);
		Optional<DetectedPractice> policyAction = policyAction(document);
		if (!policyMatches.isEmpty()) {
			CommandMatch match = policyMatches.getFirst();
			findings.add(CapabilityFinding.positive("POLICY_TOOL_PRESENT", dimension(), CAPABILITY_POLICY_AS_CODE,
					match.evidence(), match.location()));
		}
		else if (policyAction.isPresent()) {
			findings.add(CapabilityFinding.positive("POLICY_TOOL_PRESENT", dimension(), CAPABILITY_POLICY_AS_CODE,
					policyAction.get().evidence(), policyAction.get().location()));
		}
		else {
			findings.add(CapabilityFinding.missing("CHECKOV_OR_OPA_MISSING", dimension(), CAPABILITY_POLICY_AS_CODE));
		}

		return findings;
	}

	private void detectSafeActionTokenUsage(List<CapabilityFinding> findings, PipelineDocument document) {
		boolean hasPermissions = document.jobs().stream()
				.anyMatch(job -> AnalysisSupport.hasText(job.details())
						&& AnalysisSupport.lower(job.details()).contains("permissions:"));
		if (hasPermissions) {
			findings.add(CapabilityFinding.positive("PERMISSIONS_DECLARED", dimension(), CAPABILITY_SAFE_ACTION_TOKEN,
					PRACTICE_PERMISSIONS_DECLARED, "jobs"));
		}
		else {
			findings.add(CapabilityFinding.missing("MISSING_PERMISSIONS", dimension(), CAPABILITY_SAFE_ACTION_TOKEN));
		}

		boolean hasWriteAll = document.jobs().stream()
				.anyMatch(job -> AnalysisSupport.hasText(job.details())
						&& AnalysisSupport.lower(job.details()).contains("write-all"));
		if (hasWriteAll) {
			findings.add(CapabilityFinding.smell("GITHUB_TOKEN_OVERPERMISSIVE", dimension(), CAPABILITY_SAFE_ACTION_TOKEN,
					"permissions: write-all detected", "jobs"));
		}

		Optional<DetectedPractice> unpinned = unpinnedAction(document);
		if (unpinned.isPresent()) {
			findings.add(CapabilityFinding.smell("UNPINNED_ACTION_VERSION", dimension(), CAPABILITY_SAFE_ACTION_TOKEN,
					unpinned.get().evidence(), unpinned.get().location()));
		}
	}

	private Optional<DetectedPractice> policyAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				String uses = AnalysisSupport.lower(step.uses());
				if (uses.contains("bridgecrewio/checkov-action")
						|| uses.contains("open-policy-agent/conftest-action")
						|| uses.contains("aquasecurity/tfsec-action")
						|| uses.contains("checkmarx/kics-github-action")) {
					return Optional.of(new DetectedPractice(PRACTICE_POLICY_TOOL_DETECTED, step.uses(), step.location()));
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> unpinnedAction(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				String uses = step.uses();
				if (uses != null && !uses.isBlank() && uses.contains("@")) {
					if (!PINNED_SHA_PATTERN.matcher(uses).find()) {
						return Optional.of(new DetectedPractice(PRACTICE_UNPINNED_ACTION, uses, step.location()));
					}
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> hardcodedSecretSignal(PipelineDocument document) {
		for (PipelineJob job : document.jobs()) {
			Optional<DetectedPractice> found = hardcodedInDetails(job.details(), job.location());
			if (found.isPresent()) {
				return found;
			}
			for (PipelineStep step : job.steps()) {
				found = hardcodedInDetails(step.details(), step.location());
				if (found.isPresent()) {
					return found;
				}
			}
		}
		return Optional.empty();
	}

	private Optional<DetectedPractice> hardcodedInDetails(String details, String location) {
		if (details == null) {
			return Optional.empty();
		}
		Matcher matcher = HARDCODED_SECRET_PATTERN.matcher(details);
		while (matcher.find()) {
			String value = matcher.group(1);
			if (!isSecretReference(value)) {
				return Optional.of(new DetectedPractice(PRACTICE_HARDCODED_SECRET, matcher.group(), location));
			}
		}
		return Optional.empty();
	}

	private boolean isSecretReference(String value) {
		return value.startsWith("$") || value.equals("\"\"") || value.equals("''") || value.isBlank();
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
				.filter(action -> AnalysisSupport.containsAny(AnalysisSupport.lower(action.evidence()), "github/codeql-action", TOOL_SEMGREP))
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

	private List<CommandMatch> securityContext(List<CommandMatch> matches) {
		List<CommandMatch> filtered = new ArrayList<>();
		for (CommandMatch match : matches) {
			String evidence = AnalysisSupport.lower(match.evidence());
			if ((!evidence.contains(TOOL_SEMGREP) && !evidence.contains("sonar-scanner")) || securitySpecificContext(match)) {
				filtered.add(match);
			}
		}
		return filtered;
	}

	private boolean securitySpecificContext(CommandMatch match) {
		String context = AnalysisSupport.lower(AnalysisSupport.context(match.job(), match.step()));
		return AnalysisSupport.containsAny(context, KEYWORD_SECURITY, "sast", KEYWORD_VULNERABILITY, "vulnerable", KEYWORD_SECRET, "dependency", "scan");
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
				else if (uses.contains(TOOL_SEMGREP) && AnalysisSupport.containsAny(context, KEYWORD_SECURITY, "sast", KEYWORD_VULNERABILITY, KEYWORD_SECRET, "scan")) {
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
		return AnalysisSupport.containsAny(
				AnalysisSupport.lower(output.type() + " " + output.evidence()),
				"sast",
				"dependency_scanning",
				"container_scanning",
				"secret_detection");
	}

	private boolean securityJob(PipelineJob job) {
		return AnalysisSupport.containsAny(AnalysisSupport.lower(job.id() + " " + job.name() + " " + job.stage()), KEYWORD_SECURITY, "sast", KEYWORD_VULNERABILITY, KEYWORD_SECRET, "scan");
	}

	private boolean securitySarifUpload(PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("github/codeql-action/upload-sarif");
	}

	private boolean securityArtifactUpload(PipelineJob job, PipelineStep step) {
		return step.uses() != null && AnalysisSupport.lower(step.uses()).startsWith("actions/upload-artifact") && securityJob(job);
	}

}

