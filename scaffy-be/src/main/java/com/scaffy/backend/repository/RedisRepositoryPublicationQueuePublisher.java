package com.scaffy.backend.repository;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scaffy.repository.publication.queue", name = "enabled", havingValue = "true")
public class RedisRepositoryPublicationQueuePublisher implements RepositoryPublicationQueuePublisher {

	private final StringRedisTemplate redisTemplate;
	private final String queueName;

	public RedisRepositoryPublicationQueuePublisher(
			StringRedisTemplate redisTemplate,
			@Value("${scaffy.repository.publication.queue.name:scaffy:repo-publication-jobs}") String queueName) {
		this.redisTemplate = redisTemplate;
		this.queueName = queueName;
	}

	@Override
	public void enqueue(UUID publicationJobId) {
		redisTemplate.opsForList().rightPush(queueName, publicationJobId.toString());
	}
}
