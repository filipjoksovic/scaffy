package com.scaffy.backend.recommend;

public record FindingFixResponse(
		RecommendationStatus status,
		String model,
		String summary,
		String explanation,
		String language,
		String suggestedCode,
		FindingFixEdit edit,
		String message) {

	public static FindingFixResponse ok(String model, String summary, String explanation, String language,
			String suggestedCode, FindingFixEdit edit) {
		return new FindingFixResponse(RecommendationStatus.OK, model, summary, explanation, language, suggestedCode, edit,
				null);
	}

	public static FindingFixResponse unavailable(String message) {
		return new FindingFixResponse(RecommendationStatus.UNAVAILABLE, null, null, null, null, null, null, message);
	}

	public static FindingFixResponse error(String message) {
		return new FindingFixResponse(RecommendationStatus.ERROR, null, null, null, null, null, null, message);
	}
}
