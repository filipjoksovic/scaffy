package com.scaffy.backend.repository;

import java.util.UUID;

public class NoopRepositoryAnalysisQueuePublisher implements RepositoryAnalysisQueuePublisher {

	@Override
	public void enqueue(UUID analysisJobId) {
		// Analysis workers are optional in local and test environments.
	}
}
