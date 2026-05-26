package com.scaffy.backend.analyze;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AnalysisStatus {
	MISSING("missing"),
	PARTIAL("partial"),
	COMPLETE("complete"),
	NOT_EVALUATED("not_evaluated");

	private final String value;

	AnalysisStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static AnalysisStatus fromValue(String value) {
		for (AnalysisStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown analysis status: " + value);
	}
}
