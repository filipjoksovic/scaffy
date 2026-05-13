package com.scaffy.backend.analyze;

public interface CapabilityRuleSet {

	String dimension();

	DimensionAnalysis analyze(PipelineDocument document);
}
