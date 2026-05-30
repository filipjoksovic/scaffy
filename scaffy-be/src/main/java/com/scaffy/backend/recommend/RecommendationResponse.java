package com.scaffy.backend.recommend;

import java.util.List;

public record RecommendationResponse(
		RecommendationStatus status,
		String model,
		List<Recommendation> recommendations,
		String message) {

	public static RecommendationResponse ok(String model, List<Recommendation> recommendations) {
		return new RecommendationResponse(RecommendationStatus.OK, model, recommendations, null);
	}

	public static RecommendationResponse unavailable(String message) {
		return new RecommendationResponse(RecommendationStatus.UNAVAILABLE, null, List.of(), message);
	}

	public static RecommendationResponse error(String message) {
		return new RecommendationResponse(RecommendationStatus.ERROR, null, List.of(), message);
	}
}
