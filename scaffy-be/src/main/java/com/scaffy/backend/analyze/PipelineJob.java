package com.scaffy.backend.analyze;

import java.util.List;

public record PipelineJob(
		String id,
		String name,
		String stage,
		String environment,
		boolean manualOnly,
		String condition,
		String when,
		String details,
		String location,
		List<PipelineStep> steps,
		List<PipelineOutput> outputs) {

	public PipelineJob(
			String id,
			String name,
			String stage,
			String environment,
			boolean manualOnly,
			String location,
			List<PipelineStep> steps,
			List<PipelineOutput> outputs) {
		this(id, name, stage, environment, manualOnly, null, null, null, location, steps, outputs);
	}
}
