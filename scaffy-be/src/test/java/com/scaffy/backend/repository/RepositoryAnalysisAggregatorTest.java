package com.scaffy.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.BuildReleaseManagementCapabilityRuleSet;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.CodeAnalysisCapabilityRuleSet;
import com.scaffy.backend.analyze.DeploymentCapabilityRuleSet;
import com.scaffy.backend.analyze.FindingType;
import com.scaffy.backend.analyze.GitHubActionsParser;
import com.scaffy.backend.analyze.GitLabCiParser;
import com.scaffy.backend.analyze.NotificationCapabilityRuleSet;
import com.scaffy.backend.analyze.PipelineAnalyzer;
import com.scaffy.backend.analyze.ProviderDetector;
import com.scaffy.backend.analyze.ScoringEngine;
import com.scaffy.backend.analyze.SecurityScanningCapabilityRuleSet;
import com.scaffy.backend.analyze.TestCapabilityRuleSet;
import com.scaffy.backend.analyze.WorkflowQualityCapabilityRuleSet;
import com.scaffy.backend.analyze.YamlPipelineParser;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

class RepositoryAnalysisAggregatorTest {

	private final ScoringEngine scoringEngine = new ScoringEngine();
	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(
					new BuildReleaseManagementCapabilityRuleSet(),
					new TestCapabilityRuleSet(),
					new WorkflowQualityCapabilityRuleSet(),
					new CodeAnalysisCapabilityRuleSet(),
					new NotificationCapabilityRuleSet(),
					new SecurityScanningCapabilityRuleSet(),
					new DeploymentCapabilityRuleSet()),
			scoringEngine);
	private final RepositoryAnalysisAggregator aggregator = new RepositoryAnalysisAggregator(scoringEngine);

	@Test
	void keepsSingleWorkflowAnalyzerFindingsUntouched() {
		AnalysisResponse testsOnly = analyzer.analyze(".github/workflows/tests.yml", """
				name: Tests
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm ci
				      - run: npm test
				""");

		assertThat(findings(testsOnly))
				.anySatisfy(finding -> {
					assertThat(finding.ruleId()).isEqualTo("BUILD_STAGE_PRESENT");
					assertThat(finding.type()).isEqualTo(FindingType.MISSING);
				});
	}

	@Test
	void suppressesRepositoryWideMissingFindingsSatisfiedByOtherWorkflows() {
		AnalysisResponse build = analyzer.analyze(".github/workflows/build.yml", """
				name: Build
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm ci
				      - run: npm run build
				""");
		AnalysisResponse tests = analyzer.analyze(".github/workflows/tests.yml", """
				name: Tests
				on: [push]
				jobs:
				  test:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm ci
				      - run: npm test -- --coverage
				""");

		AnalysisResponse repositoryAnalysis = aggregator.aggregate(List.of(
				WorkflowAnalysisItem.success(".github/workflows/build.yml", build, WorkflowMetricsResult.unsupported()),
				WorkflowAnalysisItem.success(".github/workflows/tests.yml", tests, WorkflowMetricsResult.unsupported())));

		List<CapabilityFinding> findings = findings(repositoryAnalysis);
		assertThat(findings)
				.filteredOn(finding -> finding.type() == FindingType.MISSING)
				.extracting(CapabilityFinding::ruleId)
				.doesNotContain("BUILD_STAGE_PRESENT", "PIPELINE_MISSING_TEST_STAGE", "TESTS_NOT_AUTOMATED");
		assertThat(findings)
				.filteredOn(finding -> finding.type() == FindingType.POSITIVE)
				.extracting(CapabilityFinding::ruleId)
				.contains("BUILD_STAGE_PRESENT", "TESTS_PRESENT", "CI_INTEGRATED_TESTS");
		assertThat(repositoryAnalysis.overallLevel()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void collapsesDuplicateRepositoryWideMissingFindings() {
		AnalysisResponse frontend = analyzer.analyze(".github/workflows/frontend.yml", """
				name: Frontend
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm run build
				""");
		AnalysisResponse backend = analyzer.analyze(".github/workflows/backend.yml", """
				name: Backend
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - run: mvn package
				""");

		AnalysisResponse repositoryAnalysis = aggregator.aggregate(List.of(
				WorkflowAnalysisItem.success(".github/workflows/frontend.yml", frontend, WorkflowMetricsResult.unsupported()),
				WorkflowAnalysisItem.success(".github/workflows/backend.yml", backend, WorkflowMetricsResult.unsupported())));

		assertThat(findings(repositoryAnalysis))
				.filteredOn(finding -> finding.type() == FindingType.MISSING)
				.filteredOn(finding -> "MISSING_PACKAGE_MANAGEMENT".equals(finding.ruleId()))
				.hasSize(1);
	}

	private List<CapabilityFinding> findings(AnalysisResponse response) {
		return response.dimensions().stream()
				.flatMap(dimension -> dimension.capabilityScores().stream())
				.flatMap(capability -> capability.findings().stream())
				.toList();
	}
}
