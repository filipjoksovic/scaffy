package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScoringEngineTest {

	private final ScoringEngine scoringEngine = new ScoringEngine();

	@Test
	void scoreReturnsNotEvaluatedWhenFindingsListIsEmpty() {
		DomainScore result = scoringEngine.score("workflow_quality", List.of());

		assertThat(result.status()).isEqualTo(AnalysisStatus.NOT_EVALUATED);
		assertThat(result.score()).isEqualTo(0.0);
		assertThat(result.level()).isZero();
		assertThat(result.capabilityScores()).isEmpty();
	}

	@Test
	void scoreReturnsMissingWhenAllFindingsAreMissingType() {
		List<CapabilityFinding> findings = List.of(
				CapabilityFinding.missing("BUILD_STAGE_PRESENT", "build_release", "Build scripting"));

		DomainScore result = scoringEngine.score("build_release", findings);

		assertThat(result.status()).isEqualTo(AnalysisStatus.MISSING);
		assertThat(result.score()).isEqualTo(0.0);
	}

	@Test
	void overallStatusIsNotEvaluatedWhenAllDimensionsAreNotEvaluated() {
		List<DomainScore> domainScores = List.of(
				new DomainScore("build_release", List.of(), 0.0, 0, AnalysisStatus.NOT_EVALUATED),
				new DomainScore("testing_maturity", List.of(), 0.0, 0, AnalysisStatus.NOT_EVALUATED));

		AnalysisStatus status = scoringEngine.overallStatus(0.0, domainScores);

		assertThat(status).isEqualTo(AnalysisStatus.NOT_EVALUATED);
	}

	@Test
	void overallStatusFallsBackToScoreBasedStatusWhenAnyDimensionEvaluated() {
		List<DomainScore> domainScores = List.of(
				new DomainScore("build_release", List.of(), 0.0, 0, AnalysisStatus.NOT_EVALUATED),
				new DomainScore("testing_maturity", List.of(), 0.5, 3, AnalysisStatus.PARTIAL));

		AnalysisStatus status = scoringEngine.overallStatus(0.5, domainScores);

		assertThat(status).isEqualTo(AnalysisStatus.PARTIAL);
	}

	@Test
	void overallScoreIgnoresNotEvaluatedDimensions() {
		List<DomainScore> domainScores = List.of(
				new DomainScore("build_release", List.of(), 0.0, 0, AnalysisStatus.NOT_EVALUATED),
				new DomainScore("testing_maturity", List.of(), 0.6, 4, AnalysisStatus.PARTIAL),
				new DomainScore("deployment_automation", List.of(), 0.8, 5, AnalysisStatus.COMPLETE));

		double overall = scoringEngine.overallScore(domainScores);

		assertThat(overall).isEqualTo(0.7);
	}
}
