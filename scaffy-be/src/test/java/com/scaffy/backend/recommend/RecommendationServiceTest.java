package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.PipelineProvider;

class RecommendationServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final RecommendationPromptBuilder promptBuilder = new RecommendationPromptBuilder();

	@Test
	void returnsUnavailableWhenProviderIsNotConfigured() {
		RecommendationService service = new RecommendationService(
				new StubLlmClient(false, "n/a", null, null),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(sampleAnalysis());

		assertThat(response.status()).isEqualTo(RecommendationStatus.UNAVAILABLE);
		assertThat(response.recommendations()).isEmpty();
		assertThat(response.message()).isNotBlank();
	}

	@Test
	void returnsErrorWhenAnalysisIsNull() {
		RecommendationService service = new RecommendationService(
				new StubLlmClient(true, "gpt-4o-mini", "{\"recommendations\":[]}", null),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(null);

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.recommendations()).isEmpty();
	}

	@Test
	void returnsErrorWhenLlmCallFails() {
		RecommendationService service = new RecommendationService(
				new StubLlmClient(true, "gpt-4o-mini", null, new LlmCallException("upstream timeout")),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(sampleAnalysis());

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
		assertThat(response.recommendations()).isEmpty();
	}

	@Test
	void returnsErrorWhenResponseIsNotJson() {
		RecommendationService service = new RecommendationService(
				new StubLlmClient(true, "gpt-4o-mini", "not json", null),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(sampleAnalysis());

		assertThat(response.status()).isEqualTo(RecommendationStatus.ERROR);
	}

	@Test
	void returnsParsedRecommendationsOnHappyPath() {
		String json = """
				{
				  "recommendations": [
				    {
				      "title": "Add timeout-minutes to all jobs",
				      "description": "Each job should set timeout-minutes to prevent runaway runs.",
				      "priority": "high",
				      "reason": "Workflow quality finding MISSING_TIMEOUT was detected.",
				      "nextStep": "Add `timeout-minutes: 15` under each job."
				    },
				    {
				      "title": "Pin actions to commit SHAs",
				      "description": "Replace floating tags with full commit SHAs.",
				      "priority": "medium",
				      "reason": "UNPINNED_ACTION_VERSION smell present.",
				      "nextStep": "Replace @v4 with the 40-char SHA."
				    }
				  ]
				}
				""";
		RecommendationService service = new RecommendationService(
				new StubLlmClient(true, "gpt-4o-mini", json, null),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(sampleAnalysis());

		assertThat(response.status()).isEqualTo(RecommendationStatus.OK);
		assertThat(response.model()).isEqualTo("gpt-4o-mini");
		assertThat(response.recommendations()).hasSize(2);
		Recommendation first = response.recommendations().get(0);
		assertThat(first.title()).isEqualTo("Add timeout-minutes to all jobs");
		assertThat(first.priority()).isEqualTo(RecommendationPriority.HIGH);
		assertThat(first.nextStep()).startsWith("Add `timeout-minutes");
	}

	@Test
	void handlesPriorityWithUnknownValue() {
		String json = """
				{
				  "recommendations": [
				    {
				      "title": "Use cache",
				      "description": "Cache npm dependencies.",
				      "priority": "critical",
				      "reason": "Caching missing.",
				      "nextStep": "Add actions/setup-node@v4 cache option."
				    }
				  ]
				}
				""";
		RecommendationService service = new RecommendationService(
				new StubLlmClient(true, "gpt-4o-mini", json, null),
				promptBuilder,
				objectMapper);

		RecommendationResponse response = service.recommend(sampleAnalysis());

		assertThat(response.status()).isEqualTo(RecommendationStatus.OK);
		assertThat(response.recommendations()).hasSize(1);
		assertThat(response.recommendations().get(0).priority()).isEqualTo(RecommendationPriority.MEDIUM);
	}

	private AnalysisResponse sampleAnalysis() {
		return new AnalysisResponse(
				PipelineProvider.GITHUB_ACTIONS,
				0.2,
				2,
				AnalysisStatus.PARTIAL,
				List.of());
	}

	private static final class StubLlmClient implements LlmClient {

		private final boolean available;
		private final String model;
		private final String response;
		private final LlmCallException failure;

		private StubLlmClient(boolean available, String model, String response, LlmCallException failure) {
			this.available = available;
			this.model = model;
			this.response = response;
			this.failure = failure;
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public String model() {
			return model;
		}

		@Override
		public String complete(String systemPrompt, String userPrompt) {
			if (failure != null) {
				throw failure;
			}
			return response;
		}
	}
}
