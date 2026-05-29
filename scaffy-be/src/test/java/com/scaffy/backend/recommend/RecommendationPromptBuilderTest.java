package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.CapabilityScore;
import com.scaffy.backend.analyze.DomainScore;
import com.scaffy.backend.analyze.PipelineProvider;

class RecommendationPromptBuilderTest {

	private final RecommendationPromptBuilder builder = new RecommendationPromptBuilder();

	@Test
	void systemPromptDescribesJsonContract() {
		String prompt = builder.systemPrompt();

		assertThat(prompt).contains("recommendations");
		assertThat(prompt).contains("title");
		assertThat(prompt).contains("priority");
		assertThat(prompt).contains("nextStep");
	}

	@Test
	void userPromptIncludesDimensionAndFindingDetails() {
		AnalysisResponse analysis = new AnalysisResponse(
				PipelineProvider.GITHUB_ACTIONS,
				0.25,
				2,
				AnalysisStatus.PARTIAL,
				List.of(new DomainScore(
						"workflow_quality",
						List.of(new CapabilityScore(
								"Execution safety",
								1,
								List.of(
										CapabilityFinding.smell("MISSING_TIMEOUT", "workflow_quality", "Execution safety",
												"timeout-minutes not set", "jobs.build"),
										CapabilityFinding.positive("CONCURRENCY_CONTROL_PRESENT", "workflow_quality",
												"Execution safety", "concurrency: configured", "jobs.build")))),
						0.25,
						2,
						AnalysisStatus.PARTIAL)));

		String prompt = builder.userPrompt(analysis);

		assertThat(prompt).contains("GITHUB_ACTIONS");
		assertThat(prompt).contains("workflow_quality");
		assertThat(prompt).contains("Execution safety");
		assertThat(prompt).contains("MISSING_TIMEOUT");
		assertThat(prompt).contains("CONCURRENCY_CONTROL_PRESENT");
		assertThat(prompt).contains("smell:");
		assertThat(prompt).contains("positive:");
	}

	@Test
	void userPromptOmitsEmptyFindingBuckets() {
		AnalysisResponse analysis = new AnalysisResponse(
				PipelineProvider.GITLAB_CI,
				0.0,
				1,
				AnalysisStatus.MISSING,
				List.of(new DomainScore(
						"security_integration",
						List.of(new CapabilityScore(
								"Static analysis (SAST)",
								0,
								List.of(CapabilityFinding.missing("SAST_MISSING", "security_integration",
										"Static analysis (SAST)")))),
						0.0,
						1,
						AnalysisStatus.MISSING)));

		String prompt = builder.userPrompt(analysis);

		assertThat(prompt).contains("missing: SAST_MISSING");
		assertThat(prompt).doesNotContain("positive:");
		assertThat(prompt).doesNotContain("smell:");
	}
}
