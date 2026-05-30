package com.scaffy.backend.recommend;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffy.backend.analyze.AnalysisResponse;

@Service
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

	private final LlmClient llmClient;
	private final RecommendationPromptBuilder promptBuilder;
	private final ObjectMapper objectMapper;

	public RecommendationService(LlmClient llmClient, RecommendationPromptBuilder promptBuilder, ObjectMapper objectMapper) {
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
		this.objectMapper = objectMapper;
	}

	public RecommendationResponse recommend(AnalysisResponse analysis) {
		if (analysis == null) {
			return RecommendationResponse.error("Analysis payload is required");
		}
		if (!llmClient.isAvailable()) {
			return RecommendationResponse.unavailable("Recommendation provider is not configured");
		}

		String systemPrompt = promptBuilder.systemPrompt();
		String userPrompt = promptBuilder.userPrompt(analysis);

		String raw;
		try {
			raw = llmClient.complete(systemPrompt, userPrompt);
		}
		catch (LlmCallException ex) {
			log.warn("LLM call failed: {}", ex.getMessage());
			return RecommendationResponse.error("Recommendation provider call failed");
		}

		try {
			List<Recommendation> recommendations = parse(raw);
			return RecommendationResponse.ok(llmClient.model(), recommendations);
		}
		catch (IOException ex) {
			log.warn("LLM JSON parse failed: {} payload={}", ex.getMessage(), raw);
			return RecommendationResponse.error("Recommendation response could not be parsed");
		}
	}

	private List<Recommendation> parse(String raw) throws IOException {
		JsonNode root = objectMapper.readTree(raw);
		JsonNode listNode = root.path("recommendations");
		if (!listNode.isArray()) {
			return List.of();
		}
		return objectMapper.readerForListOf(Recommendation.class).readValue(listNode);
	}
}
