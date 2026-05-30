package com.scaffy.backend.recommend;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RecommendationPriority {
	HIGH("high"),
	MEDIUM("medium"),
	LOW("low");

	private final String value;

	RecommendationPriority(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static RecommendationPriority fromValue(String value) {
		if (value == null) {
			return MEDIUM;
		}
		for (RecommendationPriority priority : values()) {
			if (priority.value.equalsIgnoreCase(value)) {
				return priority;
			}
		}
		return MEDIUM;
	}
}
