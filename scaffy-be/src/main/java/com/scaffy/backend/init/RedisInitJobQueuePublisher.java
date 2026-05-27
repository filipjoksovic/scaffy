package com.scaffy.backend.init;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scaffy.init.queue", name = "enabled", havingValue = "true")
public class RedisInitJobQueuePublisher implements InitJobQueuePublisher {

	private final StringRedisTemplate redisTemplate;
	private final InitializerProperties properties;

	public RedisInitJobQueuePublisher(StringRedisTemplate redisTemplate, InitializerProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	@Override
	public void enqueue(UUID jobId) {
		redisTemplate.opsForList().rightPush(properties.getQueue().getName(), jobId.toString());
	}
}
