package com.scaffy.backend.analyze;

import java.util.List;

public record DimensionAnalysis(
		String dimension,
		double score,
		int level,
		AnalysisStatus status,
		Confidence confidence,
		List<DetectedPractice> detectedPractices,
		List<String> missingPractices) {
}
