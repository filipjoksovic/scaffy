package com.scaffy.backend.recommend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
	private final FindingFixRepository findingFixRepository;

	public RecommendationService(LlmClient llmClient, RecommendationPromptBuilder promptBuilder, ObjectMapper objectMapper,
			FindingFixRepository findingFixRepository) {
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
		this.objectMapper = objectMapper;
		this.findingFixRepository = findingFixRepository;
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

	public FindingFixResponse recommendFix(FindingFixRequest request) {
		if (request == null || request.finding() == null) {
			return FindingFixResponse.error("Finding payload is required");
		}

		UUID analysisRunId = request.analysisRunId();
		String findingHash = analysisRunId == null ? null : findingHash(request.finding());
		if (analysisRunId != null) {
			Optional<FindingFixResponse> stored = findingFixRepository.find(analysisRunId, findingHash);
			if (stored.isPresent()) {
				return stored.get();
			}
		}

		if (!llmClient.isAvailable()) {
			return FindingFixResponse.unavailable("Recommendation provider is not configured");
		}

		String systemPrompt = promptBuilder.fixSystemPrompt();
		String userPrompt = promptBuilder.fixUserPrompt(request);

		String raw;
		try {
			raw = llmClient.complete(systemPrompt, userPrompt);
		}
		catch (LlmCallException ex) {
			log.warn("LLM fix call failed: {}", ex.getMessage());
			return FindingFixResponse.error("Recommendation provider call failed");
		}

		FindingFixResponse response;
		try {
			response = parseFix(raw);
		}
		catch (IOException ex) {
			log.warn("LLM fix JSON parse failed: {} payload={}", ex.getMessage(), raw);
			return FindingFixResponse.error("Recommendation response could not be parsed");
		}

		if (analysisRunId != null && response.status() == RecommendationStatus.OK) {
			try {
				findingFixRepository.save(analysisRunId, findingHash, request.finding(), response);
			}
			catch (DataAccessException ex) {
				log.warn("Could not persist finding fix for run {}: {}", analysisRunId, ex.getMessage());
			}
		}

		return response;
	}

	private List<Recommendation> parse(String raw) throws IOException {
		JsonNode root = objectMapper.readTree(raw);
		JsonNode listNode = root.path("recommendations");
		if (!listNode.isArray()) {
			return List.of();
		}
		return objectMapper.readerForListOf(Recommendation.class).readValue(listNode);
	}

	private FindingFixResponse parseFix(String raw) throws IOException {
		JsonNode root = objectMapper.readTree(raw);
		String language = text(root, "language");
		return FindingFixResponse.ok(
				llmClient.model(),
				text(root, "summary"),
				text(root, "explanation"),
				language == null || language.isBlank() ? "yaml" : language,
				text(root, "suggestedCode"),
				parseEdit(root.get("edit")));
	}

	private FindingFixEdit parseEdit(JsonNode node) {
		if (node == null || !node.isObject()) {
			return null;
		}
		return new FindingFixEdit(
				text(node, "mode"),
				integer(node, "afterLine"),
				integer(node, "startLine"),
				integer(node, "endLine"),
				text(node, "code"));
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private Integer integer(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || !value.isNumber() ? null : value.asInt();
	}

	private String findingHash(FindingFixRequest.Finding finding) {
		String key = String.join("|",
				nullSafe(finding.ruleId()),
				nullSafe(finding.dimension()),
				nullSafe(finding.capability()),
				nullSafe(finding.type()),
				nullSafe(finding.location()),
				nullSafe(finding.evidence()));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available.", ex);
		}
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
