package com.scaffy.backend.analyze;

import java.util.List;

public record AnalysisResponse(
		PipelineProvider provider,
		double overallScore,
		int overallLevel,
		AnalysisStatus overallStatus,
		List<DomainScore> dimensions) {
}
