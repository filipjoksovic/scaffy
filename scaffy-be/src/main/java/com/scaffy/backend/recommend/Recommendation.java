package com.scaffy.backend.recommend;

public record Recommendation(
		String title,
		String description,
		RecommendationPriority priority,
		String reason,
		String nextStep) {
}
