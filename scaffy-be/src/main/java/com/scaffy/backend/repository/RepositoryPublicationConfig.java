package com.scaffy.backend.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryPublicationConfig {

	@Bean
	@ConditionalOnMissingBean(RepositoryPublicationQueuePublisher.class)
	RepositoryPublicationQueuePublisher noopRepositoryPublicationQueuePublisher() {
		return new NoopRepositoryPublicationQueuePublisher();
	}
}
