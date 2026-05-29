package com.scaffy.backend.recommend;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecommendationStatus {
	OK("ok"),
	UNAVAILABLE("unavailable"),
	ERROR("error");

	private final String value;

	RecommendationStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
