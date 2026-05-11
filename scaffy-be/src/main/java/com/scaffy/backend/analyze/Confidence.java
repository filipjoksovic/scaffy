package com.scaffy.backend.analyze;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Confidence {
	LOW("low"),
	MEDIUM("medium"),
	HIGH("high");

	private final String value;

	Confidence(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
