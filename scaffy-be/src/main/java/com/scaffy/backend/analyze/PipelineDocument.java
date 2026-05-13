package com.scaffy.backend.analyze;

import java.util.List;

public record PipelineDocument(
		PipelineProvider provider,
		List<PipelineTrigger> triggers,
		List<PipelineJob> jobs) {
}
