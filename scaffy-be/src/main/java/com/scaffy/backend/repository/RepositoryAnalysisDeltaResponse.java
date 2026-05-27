package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;

public record RepositoryAnalysisDeltaResponse(
		boolean hasPrevious,
		RunSummary baseRun,
		RunSummary currentRun,
		ScoreDelta overall,
		List<DimensionDelta> dimensions,
		List<CapabilityDelta> capabilities,
		List<FindingChange> findingChanges) {

	public record RunSummary(
			String runId,
			int runNumber,
			OffsetDateTime analyzedAt,
			String workflowPath,
			String workflowContentHash,
			double overallScore,
			int overallLevel,
			String overallStatus,
			int analysisSchemaVersion,
			String analyzerModelVersion) {

		static RunSummary from(RepositoryAnalysisSummary summary) {
			return new RunSummary(
					summary.id().toString(),
					summary.runNumber(),
					summary.analyzedAt(),
					summary.workflowPath(),
					summary.workflowContentHash(),
					summary.overallScore(),
					summary.overallLevel(),
					summary.overallStatus(),
					summary.analysisSchemaVersion(),
					summary.analyzerModelVersion());
		}
	}

	public record ScoreDelta(
			double baseScore,
			double currentScore,
			double scoreDelta,
			int baseLevel,
			int currentLevel,
			int levelDelta,
			String baseStatus,
			String currentStatus,
			DeltaDirection direction) {
	}

	public record DimensionDelta(
			String dimension,
			double baseScore,
			double currentScore,
			double scoreDelta,
			int baseLevel,
			int currentLevel,
			int levelDelta,
			String baseStatus,
			String currentStatus,
			DeltaDirection direction) {
	}

	public record CapabilityDelta(
			String dimension,
			String capability,
			int basePoints,
			int currentPoints,
			int pointsDelta,
			int baseFindingCount,
			int currentFindingCount,
			int findingCountDelta,
			DeltaDirection direction) {
	}

	public record FindingChange(
			String ruleId,
			String dimension,
			String capability,
			String type,
			String evidence,
			String location,
			FindingChangeKind kind,
			DeltaDirection direction) {
	}
}
