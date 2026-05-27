package com.scaffy.backend.init;

public record InitSelection(
		SelectedStack frontend,
		SelectedStack backend,
		SelectedPipeline pipeline,
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
}
