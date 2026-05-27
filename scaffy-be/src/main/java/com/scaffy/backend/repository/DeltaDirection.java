package com.scaffy.backend.repository;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeltaDirection {
	IMPROVED("improved"),
	WORSENED("worsened"),
	UNCHANGED("unchanged"),
	MIXED("mixed");

	private final String value;

	DeltaDirection(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
