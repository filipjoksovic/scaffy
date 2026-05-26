package com.scaffy.backend.repository;

import com.scaffy.backend.analyze.AnalysisResponse;

public record RepositoryAnalysisResponse(
		String repositoryId,
		String repository,
		String workflowPath,
		AnalysisResponse analysis) {
}
