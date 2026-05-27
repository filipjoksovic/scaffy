package com.scaffy.backend.repository;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRepositoryPublicationRequest(
		@NotNull(message = "Initializer job is required")
		UUID initJobId,

		@NotBlank(message = "Repository name is required")
		@Size(min = 1, max = 100, message = "Repository name must be between 1 and 100 characters")
		@Pattern(
				regexp = "^[A-Za-z0-9._-]+$",
				message = "Repository name can only use letters, digits, dots, underscores, and hyphens")
		String repositoryName,

		@Size(max = 280, message = "Description must be 280 characters or less")
		String description) {
}
