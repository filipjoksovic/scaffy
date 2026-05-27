package com.scaffy.backend.init;

public record InitSelection(
		SelectedStack frontend,
		SelectedStack backend,
		SelectedPipeline pipeline,
		SelectedMaturity pipelineMaturity,
		boolean includeDocker) {

	public record SelectedStack(
			String id,
			String name,
			String versionId,
			String versionLabel,
			String version,
			String runtimeId,
			String runtimeLabel,
			String runtime,
			String runtimeVersion) {
	}

	public record SelectedPipeline(
			String id,
			String name) {
	}

	public record SelectedMaturity(
			String id,
			String label,
			String description,
			int level,
			boolean dockerRequired) {
	}
}
