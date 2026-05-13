package com.scaffy.backend.analyze;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DetectedPractice(
		String practice,
		String evidence,
		String location,
		Map<String, String> metadata) {

	public DetectedPractice(String practice, String evidence, String location) {
		this(practice, evidence, location, Map.of());
	}
}
