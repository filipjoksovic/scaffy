package com.scaffy.backend.init;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InitializerProperties.class)
public class InitializerConfig {

	@Bean
	@ConditionalOnMissingBean(InitJobQueuePublisher.class)
	InitJobQueuePublisher noopInitJobQueuePublisher() {
		return new NoopInitJobQueuePublisher();
	}

	@Bean
	@ConditionalOnMissingBean(InitArtifactStorage.class)
	InitArtifactStorage unavailableInitArtifactStorage() {
		return new UnavailableInitArtifactStorage();
	}

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}
}
