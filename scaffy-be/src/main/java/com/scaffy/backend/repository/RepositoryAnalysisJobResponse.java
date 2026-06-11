package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;

public record RepositoryAnalysisJobResponse(
		String jobId,
		String repositoryId,
		String analysisRunId,
		String status,
		String progress,
		int progressPercent,
		String errorMessage,
		List<RepositoryAnalysisJobLogLine> logs,
		OffsetDateTime createdAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt) {

	public static RepositoryAnalysisJobResponse from(RepositoryAnalysisJob job, List<RepositoryAnalysisJobLogLine> logs) {
		return new RepositoryAnalysisJobResponse(
				job.id().toString(),
				job.repositoryConnectionId().toString(),
				job.analysisRunId() == null ? null : job.analysisRunId().toString(),
				job.status(),
				job.progressMessage(),
				job.progressPercent(),
				job.errorMessage(),
				logs,
				job.createdAt(),
				job.startedAt(),
				job.completedAt());
	}
}
