package com.scaffy.backend.recommend;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

	private final RecommendationProperties.OpenAi config;
	private final RestClient restClient;

	public OpenAiClient(RecommendationProperties properties) {
		this.config = properties.openai();
		this.restClient = RestClient.builder()
				.baseUrl(config.baseUrl())
				.build();
	}

	@Override
	public boolean isAvailable() {
		return config.apiKey() != null && !config.apiKey().isBlank();
	}

	@Override
	public String model() {
		return config.model();
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		if (!isAvailable()) {
			throw new LlmCallException("OpenAI API key not configured");
		}

		Map<String, Object> body = Map.of(
				"model", config.model(),
				"temperature", config.temperature(),
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userPrompt)));

		try {
			Map<?, ?> response = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + config.apiKey())
					.body(body)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (req, res) -> {
						throw new LlmCallException("OpenAI returned status " + res.getStatusCode());
					})
					.body(Map.class);

			return extractContent(response);
		}
		catch (LlmCallException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			log.warn("OpenAI call failed: {}", ex.getMessage());
			throw new LlmCallException("OpenAI HTTP call failed: " + ex.getMessage(), ex);
		}
	}

	private String extractContent(Map<?, ?> response) {
		if (response == null) {
			throw new LlmCallException("OpenAI returned empty response body");
		}
		Object choicesObject = response.get("choices");
		if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
			throw new LlmCallException("OpenAI response missing choices");
		}
		Object first = choices.get(0);
		if (!(first instanceof Map<?, ?> choice)) {
			throw new LlmCallException("OpenAI choice malformed");
		}
		Object messageObject = choice.get("message");
		if (!(messageObject instanceof Map<?, ?> message)) {
			throw new LlmCallException("OpenAI choice message missing");
		}
		Object content = message.get("content");
		if (!(content instanceof String text) || text.isBlank()) {
			throw new LlmCallException("OpenAI message content empty");
		}
		return text;
	}
}
