package com.scaffy.backend.init.favourite;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user-saved stack combination that can be recalled to pre-fill the
 * Initializer wizard.
 */
public record FavouriteStack(
		UUID id,
		UUID userId,
		String name,
		String frontend,
		String frontendVersion,
		String frontendRuntime,
		String backend,
		String backendVersion,
		String backendRuntime,
		String pipeline,
		String pipelineMaturity,
		boolean includeDocker,
		OffsetDateTime createdAt) {
}
