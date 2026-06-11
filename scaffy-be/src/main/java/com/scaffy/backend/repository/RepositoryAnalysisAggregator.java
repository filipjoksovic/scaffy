package com.scaffy.backend.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.DomainScore;
import com.scaffy.backend.analyze.FindingType;
import com.scaffy.backend.analyze.PipelineProvider;
import com.scaffy.backend.analyze.ScoringEngine;

@Component
public class RepositoryAnalysisAggregator {

	private static final Map<String, Set<String>> REPOSITORY_WIDE_MISSING_RULES = Map.ofEntries(
			Map.entry("BUILD_STAGE_PRESENT", Set.of("BUILD_STAGE_PRESENT")),
			Map.entry("MISSING_PACKAGE_MANAGEMENT", Set.of("DEPENDENCY_INSTALL_PRESENT", "DETERMINISTIC_INSTALL_PRESENT")),
			Map.entry("PIPELINE_MISSING_ARTIFACT_PUBLISH", Set.of("ARTIFACT_OUTPUT_PRESENT")),
			Map.entry("NO_RELEASE_STAGE", Set.of("REGISTRY_PUBLISH_PRESENT", "ARTIFACT_REUSE_PRESENT")),
			Map.entry("RELEASE_TAGGING_PRESENT", Set.of("VERSIONED_ARTIFACT")),
			Map.entry("PIPELINE_MISSING_TEST_STAGE", Set.of("TESTS_PRESENT")),
			Map.entry("TESTS_NOT_AUTOMATED", Set.of("CI_INTEGRATED_TESTS")),
			Map.entry("NO_TEST_REPORT_OUTPUT", Set.of("TEST_REPORT_OUTPUT_PRESENT")),
			Map.entry("NO_COVERAGE_TOOL", Set.of("COVERAGE_TOOL_PRESENT")),
			Map.entry("LINT_STATIC_MISSING", Set.of("LINT_STATIC_PRESENT")),
			Map.entry("FORMATTER_MISSING", Set.of("FORMATTER_PRESENT")),
			Map.entry("TYPE_CHECK_MISSING", Set.of("TYPE_CHECK_PRESENT")),
			Map.entry("NOTIFICATION_MISSING", Set.of("NOTIFICATION_CHANNEL_PRESENT")),
			Map.entry("STATUS_CONDITION_MISSING", Set.of("STATUS_CONDITION_PRESENT")),
			Map.entry("SAST_MISSING", Set.of("SAST_PRESENT")),
			Map.entry("DEPENDENCY_SCAN_MISSING", Set.of("DEPENDENCY_SCAN_PRESENT", "CONTAINER_SCAN_PRESENT")),
			Map.entry("SECRET_SCAN_MISSING", Set.of("SECRET_SCAN_PRESENT")),
			Map.entry("CHECKOV_OR_OPA_MISSING", Set.of("POLICY_TOOL_PRESENT")),
			Map.entry("NO_DEPLOYMENT_STAGE", Set.of("DEPLOYMENT_STAGE_PRESENT")),
			Map.entry("MISSING_ENVIRONMENT_DECLARATION", Set.of("ENVIRONMENT_DECLARED")),
			Map.entry("IaC_NOT_PRESENT", Set.of("IAC_PRESENT", "ARTIFACT_IMAGE_USED")),
			Map.entry("MULTI_STAGE_PIPELINE_PRESENT", Set.of("MULTI_STAGE_PIPELINE_PRESENT")),
			Map.entry("NO_ROLLBACK_ON_FAILURE", Set.of("ROLLBACK_SIGNAL_PRESENT")));

	private final ScoringEngine scoringEngine;

	public RepositoryAnalysisAggregator(ScoringEngine scoringEngine) {
		this.scoringEngine = scoringEngine;
	}

	public AnalysisResponse aggregate(List<WorkflowAnalysisItem> successfulAnalyses) {
		PipelineProvider provider = successfulAnalyses.get(0).analysis().provider();

		Map<String, List<CapabilityFinding>> findingsByDimension = new LinkedHashMap<>();
		for (WorkflowAnalysisItem workflow : successfulAnalyses) {
			for (DomainScore dimension : workflow.analysis().dimensions()) {
				List<CapabilityFinding> findings = dimension.capabilityScores().stream()
						.flatMap(capability -> capability.findings().stream())
						.toList();
				findingsByDimension.computeIfAbsent(dimension.dimension(), ignored -> new ArrayList<>())
						.addAll(findings);
			}
		}

		List<DomainScore> domainScores = findingsByDimension.entrySet().stream()
				.map(entry -> scoringEngine.score(entry.getKey(), repositoryLevelFindings(entry.getValue())))
				.toList();
		List<CapabilityFinding> allFindings = domainScores.stream()
				.flatMap(dimension -> dimension.capabilityScores().stream())
				.flatMap(capability -> capability.findings().stream())
				.toList();
		double overallScore = scoringEngine.overallScore(domainScores);
		int overallLevel = scoringEngine.maturityLevel(overallScore, domainScores, allFindings);
		return new AnalysisResponse(
				provider,
				overallScore,
				overallLevel,
				scoringEngine.overallStatus(overallScore, domainScores),
				domainScores);
	}

	private List<CapabilityFinding> repositoryLevelFindings(List<CapabilityFinding> findings) {
		Set<String> positiveRuleIds = findings.stream()
				.filter(finding -> finding.type() == FindingType.POSITIVE)
				.map(CapabilityFinding::ruleId)
				.collect(LinkedHashSet::new, Set::add, Set::addAll);
		return findings.stream()
				.filter(finding -> !isSatisfiedRepositoryWideMissing(finding, positiveRuleIds))
				.filter(new HashSet<>()::add)
				.toList();
	}

	private boolean isSatisfiedRepositoryWideMissing(CapabilityFinding finding, Set<String> positiveRuleIds) {
		if (finding.type() != FindingType.MISSING) {
			return false;
		}
		Set<String> satisfyingRules = REPOSITORY_WIDE_MISSING_RULES.get(finding.ruleId());
		return satisfyingRules != null && satisfyingRules.stream().anyMatch(positiveRuleIds::contains);
	}
}
