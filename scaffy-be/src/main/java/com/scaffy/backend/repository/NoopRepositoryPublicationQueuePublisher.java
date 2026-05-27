package com.scaffy.backend.repository;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RepositoryPublicationQueuePublisher.class)
public class NoopRepositoryPublicationQueuePublisher implements RepositoryPublicationQueuePublisher {

	@Override
	public void enqueue(UUID publicationJobId) {
		// Publication workers are optional in local environments.
	}
}
