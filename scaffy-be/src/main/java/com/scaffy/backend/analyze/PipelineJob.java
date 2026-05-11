package com.scaffy.backend.analyze;

import java.util.List;

public record PipelineJob(
		String id,
		String name,
		String stage,
		boolean manualOnly,
		String location,
		List<PipelineStep> steps,
		List<PipelineOutput> outputs) {
}
