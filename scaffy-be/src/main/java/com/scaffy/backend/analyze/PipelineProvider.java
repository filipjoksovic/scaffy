package com.scaffy.backend.analyze;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PipelineProvider {
	GITHUB_ACTIONS("github-actions"),
	GITLAB_CI("gitlab-ci");

	private final String value;

	PipelineProvider(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static PipelineProvider fromValue(String value) {
		for (PipelineProvider provider : values()) {
			if (provider.value.equals(value)) {
				return provider;
			}
		}
		throw new IllegalArgumentException("Unknown pipeline provider: " + value);
	}
}
