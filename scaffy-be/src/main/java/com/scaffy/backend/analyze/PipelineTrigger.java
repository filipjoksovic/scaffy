package com.scaffy.backend.analyze;

public record PipelineTrigger(
		String name,
		boolean automatic,
		String location) {
}
