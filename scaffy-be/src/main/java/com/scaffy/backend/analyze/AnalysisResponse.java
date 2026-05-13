package com.scaffy.backend.analyze;

import java.util.List;

public record AnalysisResponse(
		PipelineProvider provider,
		List<DimensionAnalysis> dimensions) {
}
