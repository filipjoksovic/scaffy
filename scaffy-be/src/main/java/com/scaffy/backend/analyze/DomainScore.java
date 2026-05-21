package com.scaffy.backend.analyze;

import java.util.List;

public record DomainScore(
		String dimension,
		List<CapabilityScore> capabilityScores,
		double score,
		int level,
		AnalysisStatus status) {
}
