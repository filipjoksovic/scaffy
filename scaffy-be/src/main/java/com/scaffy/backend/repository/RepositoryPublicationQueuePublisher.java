package com.scaffy.backend.repository;

import java.util.UUID;

public interface RepositoryPublicationQueuePublisher {
	void enqueue(UUID publicationJobId);
}
