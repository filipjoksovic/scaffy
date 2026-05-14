package com.scaffy.backend.analyze;

record CommandMatch(
		PipelineJob job,
		PipelineStep step,
		String evidence,
		String location,
		int position,
		CommandRule rule) {
}
