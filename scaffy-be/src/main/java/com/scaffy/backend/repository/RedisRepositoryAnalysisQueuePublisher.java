package com.scaffy.backend.repository;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scaffy.repository.analysis.queue", name = "enabled", havingValue = "true")
public class RedisRepositoryAnalysisQueuePublisher implements RepositoryAnalysisQueuePublisher {

	private final StringRedisTemplate redisTemplate;
	private final RepositoryAnalysisProperties properties;

	public RedisRepositoryAnalysisQueuePublisher(
			StringRedisTemplate redisTemplate,
			RepositoryAnalysisProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	@Override
	public void enqueue(UUID analysisJobId) {
		redisTemplate.opsForList().rightPush(properties.getQueue().getName(), analysisJobId.toString());
	}
}
