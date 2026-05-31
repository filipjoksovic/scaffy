package com.scaffy.backend.init.favourite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for saving a favourite stack.
 * Captures the full stack selection (excluding project name, which varies
 * per generation) plus a user-supplied display name.
 */
public record FavouriteStackRequest(
		@NotBlank(message = "Favourite name is required")
		@Size(max = 64, message = "Favourite name must be 64 characters or fewer")
		String name,

		@NotBlank(message = "Frontend stack is required")
		String frontend,

		@NotBlank(message = "Frontend version is required")
		String frontendVersion,

		@NotBlank(message = "Frontend runtime is required")
		String frontendRuntime,

		@NotBlank(message = "Backend stack is required")
		String backend,

		@NotBlank(message = "Backend version is required")
		String backendVersion,

		@NotBlank(message = "Backend runtime is required")
		String backendRuntime,

		@NotBlank(message = "Pipeline is required")
		String pipeline,

		@NotBlank(message = "Pipeline maturity is required")
		String pipelineMaturity,

		boolean includeDocker) {
}
