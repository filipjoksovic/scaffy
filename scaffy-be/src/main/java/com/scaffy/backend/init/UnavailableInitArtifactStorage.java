package com.scaffy.backend.init;

public class UnavailableInitArtifactStorage implements InitArtifactStorage {

	@Override
	public InitArtifact download(String objectKey) {
		throw new IllegalStateException("Initializer artifact storage is not configured.");
	}
}
