package com.scaffy.backend.recommend;

public record FindingFixApplyResponse(
		RecommendationStatus status,
		String commitSha,
		String commitUrl,
		String branch,
		String message) {

	public static FindingFixApplyResponse ok(String commitSha, String commitUrl, String branch) {
		return new FindingFixApplyResponse(RecommendationStatus.OK, commitSha, commitUrl, branch, null);
	}

	public static FindingFixApplyResponse unavailable(String message) {
		return new FindingFixApplyResponse(RecommendationStatus.UNAVAILABLE, null, null, null, message);
	}

	public static FindingFixApplyResponse error(String message) {
		return new FindingFixApplyResponse(RecommendationStatus.ERROR, null, null, null, message);
	}
}
