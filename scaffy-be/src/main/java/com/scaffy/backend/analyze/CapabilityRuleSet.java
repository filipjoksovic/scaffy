package com.scaffy.backend.analyze;

import java.util.List;

public interface CapabilityRuleSet {

	String dimension();

	List<CapabilityFinding> detect(PipelineDocument document);
}
