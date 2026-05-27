package com.scaffy.backend.analyze;

public record CapabilityFinding(
		String ruleId,
		String dimension,
		String capability,
		FindingType type,
		String evidence,
		String location,
		SourceSpan source) {

	public CapabilityFinding(String ruleId, String dimension, String capability, FindingType type,
			String evidence, String location) {
		this(ruleId, dimension, capability, type, evidence, location, null);
	}

	public static CapabilityFinding positive(String ruleId, String dimension, String capability,
			String evidence, String location) {
		return new CapabilityFinding(ruleId, dimension, capability, FindingType.POSITIVE, evidence, location);
	}

	public static CapabilityFinding smell(String ruleId, String dimension, String capability,
			String evidence, String location) {
		return new CapabilityFinding(ruleId, dimension, capability, FindingType.SMELL, evidence, location);
	}

	public static CapabilityFinding missing(String ruleId, String dimension, String capability) {
		return new CapabilityFinding(ruleId, dimension, capability, FindingType.MISSING, null, null);
	}

	public CapabilityFinding withSource(SourceSpan source) {
		return new CapabilityFinding(ruleId, dimension, capability, type, evidence, location, source);
	}
}
