package com.scaffy.backend.init;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovers jobs the generator can no longer finish and releases queued retries.
 * Only active when the Redis queue is enabled, since requeueing means pushing ids
 * back onto that queue.
 */
@Component
@ConditionalOnProperty(prefix = "scaffy.init.queue", name = "enabled", havingValue = "true")
public class InitJobReaper {

	private static final Logger log = LoggerFactory.getLogger(InitJobReaper.class);

	private final InitGenerationJobRepository repository;
	private final InitJobQueuePublisher queuePublisher;
	private final InitializerProperties properties;

	public InitJobReaper(
			InitGenerationJobRepository repository,
			InitJobQueuePublisher queuePublisher,
			InitializerProperties properties) {
		this.repository = repository;
		this.queuePublisher = queuePublisher;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${scaffy.init.jobs.reaper-interval-ms:20000}")
	public void reap() {
		try {
			recoverStaleRunning();
			releaseDueRetries();
		} catch (RuntimeException ex) {
			log.warn("Initializer job reaper sweep failed", ex);
		}
	}

	private void recoverStaleRunning() {
		Duration lease = Duration.ofSeconds(properties.getJobs().getLeaseTimeoutSeconds());

		List<UUID> requeued = repository.requeueStaleRunning(lease);
		for (UUID id : requeued) {
			queuePublisher.enqueue(id);
		}
		if (!requeued.isEmpty()) {
			log.info("Requeued {} stale running initializer job(s)", requeued.size());
		}

		int failed = repository.failExhaustedStaleRunning(lease);
		if (failed > 0) {
			log.info("Failed {} stale initializer job(s) with no attempts left", failed);
		}
	}

	private void releaseDueRetries() {
		List<UUID> due = repository.claimDueRetries();
		for (UUID id : due) {
			queuePublisher.enqueue(id);
		}
		if (!due.isEmpty()) {
			log.info("Released {} initializer job retry(ies) to the queue", due.size());
		}
	}
}
