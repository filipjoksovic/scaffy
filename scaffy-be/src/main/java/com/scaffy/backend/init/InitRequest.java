package com.scaffy.backend.init;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitRequest(

		@NotBlank(message = "projectName must not be blank")
		@Size(min = 2, max = 64, message = "projectName must be 2-64 characters")
		@Pattern(
				regexp = "^[a-z][a-z0-9-]*[a-z0-9]$",
				message = "projectName must be lowercase, start with a letter, and contain only letters, digits and hyphens"
		)
		String projectName,

		@NotBlank(message = "frontend must not be blank")
		String frontend,

		@NotBlank(message = "backend must not be blank")
		String backend,

		@NotBlank(message = "pipeline must not be blank")
		String pipeline
) {
}
