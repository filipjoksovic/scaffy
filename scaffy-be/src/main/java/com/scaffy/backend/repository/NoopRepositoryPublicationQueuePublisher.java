package com.scaffy.backend.repository;

import java.util.UUID;

public class NoopRepositoryPublicationQueuePublisher implements RepositoryPublicationQueuePublisher {

	@Override
	public void enqueue(UUID publicationJobId) {
		// Publication workers are optional in local environments.
	}
}
