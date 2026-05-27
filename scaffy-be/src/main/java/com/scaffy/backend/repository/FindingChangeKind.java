package com.scaffy.backend.repository;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FindingChangeKind {
	ADDED("added"),
	REMOVED("removed"),
	UNCHANGED("unchanged");

	private final String value;

	FindingChangeKind(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
