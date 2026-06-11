package com.scaffy.backend.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RepositoryAnalysisProperties.class)
public class RepositoryAnalysisConfig {

	@Bean
	@ConditionalOnMissingBean(RepositoryAnalysisQueuePublisher.class)
	RepositoryAnalysisQueuePublisher noopRepositoryAnalysisQueuePublisher() {
		return new NoopRepositoryAnalysisQueuePublisher();
	}
}
