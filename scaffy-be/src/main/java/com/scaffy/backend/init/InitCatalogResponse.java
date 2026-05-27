package com.scaffy.backend.init;

import java.util.List;

public record InitCatalogResponse(
		List<StackOption> frontends,
		List<StackOption> backends,
		List<PipelineOption> pipelines) {

	public record StackOption(
			String id,
			String name,
			String description,
			String defaultVersionId,
			List<VersionPreset> versions) {
	}

	public record VersionPreset(
			String id,
			String label,
			String version,
			String defaultRuntimeId,
			List<RuntimePreset> runtimes) {
	}

	public record RuntimePreset(
			String id,
			String label,
			String runtime,
			String version,
			boolean lts) {
	}

	public record PipelineOption(
			String id,
			String name,
			String description) {
	}
}
