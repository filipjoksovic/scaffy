package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ScoringEngine {

	private static final Map<String, Integer> POSITIVE_RULE_POINTS = Map.ofEntries(
			Map.entry("BUILD_STAGE_PRESENT", 1),
			Map.entry("BUILD_AUTOMATIC_TRIGGER", 1),
			Map.entry("DEPENDENCY_INSTALL_PRESENT", 1),
			Map.entry("DETERMINISTIC_INSTALL_PRESENT", 4),
			Map.entry("ARTIFACT_OUTPUT_PRESENT", 4),
			Map.entry("REGISTRY_PUBLISH_PRESENT", 4),
			Map.entry("ARTIFACT_REUSE_PRESENT", 2),
			Map.entry("VERSIONED_ARTIFACT", 4),
			Map.entry("TESTS_PRESENT", 4),
			Map.entry("CI_INTEGRATED_TESTS", 4),
			Map.entry("TEST_REPORT_OUTPUT_PRESENT", 2),
			Map.entry("COVERAGE_TOOL_PRESENT", 2),
			Map.entry("MULTI_LAYER_TEST_SIGNAL", 4),
			Map.entry("TIMEOUT_PRESENT", 2),
			Map.entry("CONCURRENCY_CONTROL_PRESENT", 2),
			Map.entry("PATH_FILTERS_PRESENT", 4),
			Map.entry("NAMED_RUN_STEPS_PRESENT", 4),
			Map.entry("PINNED_RUNNER_PRESENT", 2),
			Map.entry("PINNED_ACTION_VERSIONS", 2),
			Map.entry("MULTI_OS_TEST_PRESENT", 1),
			Map.entry("MULTI_VERSION_TEST_PRESENT", 1),
			Map.entry("CACHE_SIGNAL_PRESENT", 2),
			Map.entry("LINT_STATIC_PRESENT", 4),
			Map.entry("FORMATTER_PRESENT", 4),
			Map.entry("TYPE_CHECK_PRESENT", 4),
			Map.entry("NOTIFICATION_CHANNEL_PRESENT", 4),
			Map.entry("STATUS_CONDITION_PRESENT", 4),
			Map.entry("SAST_PRESENT", 4),
			Map.entry("DEPENDENCY_SCAN_PRESENT", 4),
			Map.entry("CONTAINER_SCAN_PRESENT", 4),
			Map.entry("SECRET_SCAN_PRESENT", 4),
			Map.entry("PERMISSIONS_DECLARED", 4),
			Map.entry("POLICY_TOOL_PRESENT", 4),
			Map.entry("DEPLOYMENT_STAGE_PRESENT", 4),
			Map.entry("ENVIRONMENT_DECLARED", 4),
			Map.entry("IAC_PRESENT", 4),
			Map.entry("ARTIFACT_IMAGE_USED", 4),
			Map.entry("MULTI_STAGE_PIPELINE_PRESENT", 4),
			Map.entry("ROLLBACK_SIGNAL_PRESENT", 4));

	public DomainScore score(String dimension, List<CapabilityFinding> findings) {
		if (findings.isEmpty()) {
			return new DomainScore(dimension, List.of(), 0.0, 0, AnalysisStatus.NOT_EVALUATED);
		}

		Map<String, List<CapabilityFinding>> byCapability = findings.stream()
				.collect(Collectors.groupingBy(CapabilityFinding::capability, LinkedHashMap::new, Collectors.toList()));

		List<CapabilityScore> capabilityScores = new ArrayList<>();
		for (Map.Entry<String, List<CapabilityFinding>> entry : byCapability.entrySet()) {
			int points = capabilityPoints(entry.getValue());
			capabilityScores.add(new CapabilityScore(entry.getKey(), points, entry.getValue()));
		}

		double domainScore = domainScore(capabilityScores);
		int level = AnalysisSupport.level(domainScore);
		AnalysisStatus status = domainStatus(domainScore);

		return new DomainScore(dimension, capabilityScores, domainScore, level, status);
	}

	public double overallScore(List<DomainScore> domainScores) {
		List<DomainScore> evaluated = domainScores.stream()
				.filter(d -> d.status() != AnalysisStatus.NOT_EVALUATED)
				.toList();
		if (evaluated.isEmpty()) {
			return 0.0;
		}
		double sum = evaluated.stream().mapToDouble(DomainScore::score).sum();
		return AnalysisSupport.round(sum / evaluated.size());
	}

	public AnalysisStatus overallStatus(double overallScore, List<DomainScore> domainScores) {
		if (!domainScores.isEmpty() && domainScores.stream().allMatch(d -> d.status() == AnalysisStatus.NOT_EVALUATED)) {
			return AnalysisStatus.NOT_EVALUATED;
		}
		return AnalysisSupport.status(overallScore);
	}

	public int maturityLevel(double overallScore, List<DomainScore> domainScores, List<CapabilityFinding> allFindings) {
		int level = AnalysisSupport.level(overallScore);
		if (level >= 2 && !meetsL2Criteria(allFindings)) {
			level = 1;
		}
		if (level >= 3 && !meetsL3Criteria(allFindings)) {
			level = 2;
		}
		if (level >= 4 && !meetsL4Criteria(domainScores)) {
			level = 3;
		}
		return level;
	}

	private int capabilityPoints(List<CapabilityFinding> findings) {
		int positives = findings.stream()
				.filter(f -> f.type() == FindingType.POSITIVE)
				.mapToInt(this::positivePoints)
				.sum();
		long smells = findings.stream().filter(f -> f.type() == FindingType.SMELL).count();
		return (int) Math.max(0, Math.min(positives, 4) - smells);
	}

	private int positivePoints(CapabilityFinding finding) {
		return POSITIVE_RULE_POINTS.getOrDefault(finding.ruleId(), 1);
	}

	private double domainScore(List<CapabilityScore> capabilityScores) {
		if (capabilityScores.isEmpty()) {
			return 0.0;
		}
		int total = capabilityScores.stream().mapToInt(CapabilityScore::points).sum();
		return AnalysisSupport.round((double) total / (4.0 * capabilityScores.size()));
	}

	private AnalysisStatus domainStatus(double score) {
		if (score == 0.0) {
			return AnalysisStatus.MISSING;
		}
		if (score >= 0.8) {
			return AnalysisStatus.COMPLETE;
		}
		return AnalysisStatus.PARTIAL;
	}

	private boolean meetsL2Criteria(List<CapabilityFinding> findings) {
		boolean hasBuild = findings.stream()
				.anyMatch(f -> "BUILD_STAGE_PRESENT".equals(f.ruleId()) && f.type() == FindingType.POSITIVE);
		boolean hasTest = findings.stream()
				.noneMatch(f -> "PIPELINE_MISSING_TEST_STAGE".equals(f.ruleId()) && f.type() == FindingType.MISSING);
		return hasBuild && hasTest;
	}

	private boolean meetsL3Criteria(List<CapabilityFinding> findings) {
		return findings.stream()
				.anyMatch(f -> f.type() == FindingType.POSITIVE
						&& ("VERSIONED_ARTIFACT".equals(f.ruleId()) || "ARTIFACT_OUTPUT_PRESENT".equals(f.ruleId())));
	}

	private boolean meetsL4Criteria(List<DomainScore> domainScores) {
		boolean securityPresent = domainScores.stream()
				.filter(d -> "security_integration".equals(d.dimension()))
				.anyMatch(d -> d.score() > 0);
		boolean deploymentPresent = domainScores.stream()
				.filter(d -> "deployment_automation".equals(d.dimension()))
				.anyMatch(d -> d.score() > 0);
		return securityPresent && deploymentPresent;
	}
}
