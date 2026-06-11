package com.scaffy.backend.repository;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scaffy.repository.analysis.worker", name = "enabled", havingValue = "true")
public class RepositoryAnalysisWorker implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisWorker.class);

	private final StringRedisTemplate redisTemplate;
	private final RepositoryAnalysisProperties properties;
	private final RepositoryAnalysisJobService jobService;
	private final AtomicBoolean running = new AtomicBoolean(true);

	public RepositoryAnalysisWorker(
			StringRedisTemplate redisTemplate,
			RepositoryAnalysisProperties properties,
			RepositoryAnalysisJobService jobService) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.jobService = jobService;
	}

	@Override
	public void run(ApplicationArguments args) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));
		String queue = properties.getQueue().getName();
		log.info("Repository analysis worker started queue={}", queue);
		while (running.get()) {
			String value = redisTemplate.opsForList().leftPop(
					queue,
					Duration.ofSeconds(properties.getWorker().getPollTimeoutSeconds()));
			if (value == null || value.isBlank()) {
				continue;
			}
			UUID jobId;
			try {
				jobId = UUID.fromString(value);
			}
			catch (IllegalArgumentException ex) {
				log.warn("Ignoring malformed repository analysis job id from queue: {}", value);
				continue;
			}
			runJob(jobId);
		}
	}

	private void runJob(UUID jobId) {
		Thread heartbeat = new Thread(() -> heartbeat(jobId), "repository-analysis-heartbeat-" + jobId);
		heartbeat.setDaemon(true);
		heartbeat.start();
		try {
			jobService.execute(jobId);
		}
		catch (RuntimeException ex) {
			log.warn("Repository analysis job {} failed: {}", jobId, ex.getMessage());
		}
		finally {
			heartbeat.interrupt();
		}
	}

	private void heartbeat(UUID jobId) {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(properties.getWorker().getHeartbeatIntervalMs());
				jobService.heartbeat(jobId);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			catch (RuntimeException ex) {
				log.warn("Repository analysis job {} heartbeat failed: {}", jobId, ex.getMessage());
			}
		}
	}
}
