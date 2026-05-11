package com.scaffy.backend.analyze;

public record PipelineStep(
		String command,
		String uses,
		String location,
		int index) {
}
