package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.PipelineProvider;

class RecommendationControllerTest {

	@Test
	void delegatesToServiceAndReturnsResult() {
		AtomicReference<AnalysisResponse> capturedAnalysis = new AtomicReference<>();
		RecommendationResponse stubbed = RecommendationResponse.ok(
				"gpt-4o-mini",
				List.of(new Recommendation(
						"Pin actions",
						"Replace floating tags with SHAs.",
						RecommendationPriority.MEDIUM,
						"UNPINNED_ACTION_VERSION smell.",
						"Replace @v4 with the 40-char SHA.")));

		RecommendationService service = new RecommendationService(
				new NoopLlmClient(),
				new RecommendationPromptBuilder(),
				new ObjectMapper()) {
			@Override
			public RecommendationResponse recommend(AnalysisResponse analysis) {
				capturedAnalysis.set(analysis);
				return stubbed;
			}
		};

		RecommendationController controller = new RecommendationController(service);
		AnalysisResponse input = new AnalysisResponse(
				PipelineProvider.GITHUB_ACTIONS,
				0.0,
				1,
				AnalysisStatus.MISSING,
				List.of());

		RecommendationResponse result = controller.recommend(input);

		assertThat(result).isSameAs(stubbed);
		assertThat(capturedAnalysis.get()).isSameAs(input);
	}

	private static final class NoopLlmClient implements LlmClient {

		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public String model() {
			return "noop";
		}

		@Override
		public String complete(String systemPrompt, String userPrompt) {
			throw new UnsupportedOperationException();
		}
	}
}
