package com.scaffy.backend.repository;

import java.util.UUID;

public interface RepositoryAnalysisQueuePublisher {

	void enqueue(UUID analysisJobId);
}
