package com.scaffy.backend.repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scaffy.repository.analysis.queue", name = "enabled", havingValue = "true")
public class RepositoryAnalysisJobReaper {

	private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisJobReaper.class);

	private final RepositoryAnalysisJobRepository repository;
	private final RepositoryAnalysisQueuePublisher queuePublisher;
	private final RepositoryAnalysisProperties properties;

	public RepositoryAnalysisJobReaper(
			RepositoryAnalysisJobRepository repository,
			RepositoryAnalysisQueuePublisher queuePublisher,
			RepositoryAnalysisProperties properties) {
		this.repository = repository;
		this.queuePublisher = queuePublisher;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${scaffy.repository.analysis.jobs.reaper-interval-ms:20000}")
	public void reap() {
		Duration lease = Duration.ofSeconds(properties.getJobs().getLeaseTimeoutSeconds());
		List<UUID> requeued = repository.requeueStaleRunning(lease);
		for (UUID id : requeued) {
			queuePublisher.enqueue(id);
		}
		if (!requeued.isEmpty()) {
			log.info("Requeued {} stale repository analysis job(s)", requeued.size());
		}
		int failed = repository.failExhaustedStaleRunning(lease);
		if (failed > 0) {
			log.info("Failed {} exhausted stale repository analysis job(s)", failed);
		}
		List<UUID> due = repository.claimDueRetries();
		for (UUID id : due) {
			queuePublisher.enqueue(id);
		}
		if (!due.isEmpty()) {
			log.info("Released {} repository analysis retry job(s)", due.size());
		}
	}
}
