package com.scaffy.backend.repository;

import jakarta.validation.constraints.NotBlank;

public record ConnectRepositoryRequest(
		@NotBlank String repository,
		String provider,
		String instance) {

	public String providerOrDefault() {
		return provider == null || provider.isBlank() ? "github" : provider.trim().toLowerCase();
	}

	public String instanceOrEmpty() {
		return instance == null ? "" : instance.trim().toLowerCase();
	}
}
