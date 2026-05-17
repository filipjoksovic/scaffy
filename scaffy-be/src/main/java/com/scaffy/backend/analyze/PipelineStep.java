package com.scaffy.backend.analyze;

public record PipelineStep(
		String command,
		String uses,
		String name,
		String condition,
		String details,
		String location,
		int index) {

	public PipelineStep(String command, String uses, String location, int index) {
		this(command, uses, null, null, null, location, index);
	}
}
