package com.scaffy.backend.init;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitJobRequest(
		@NotBlank(message = "Project name is required")
		@Size(min = 2, max = 64, message = "Project name must be between 2 and 64 characters")
		@Pattern(
				regexp = "^[a-z][a-z0-9-]*[a-z0-9]$",
				message = "Project name must use lowercase letters, digits, and hyphens; start with a letter; and end with a letter or digit")
		String projectName,

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

		boolean includeDocker) {
}
