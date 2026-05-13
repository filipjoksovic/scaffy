package com.scaffy.backend.analyze;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AnalysisStatus {
	MISSING("missing"),
	PARTIAL("partial"),
	COMPLETE("complete");

	private final String value;

	AnalysisStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
