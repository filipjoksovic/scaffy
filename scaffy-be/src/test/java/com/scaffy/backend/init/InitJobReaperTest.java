package com.scaffy.backend.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitJobReaperTest {

	private final InitGenerationJobRepository repository = mock(InitGenerationJobRepository.class);
	private final InitJobQueuePublisher queuePublisher = mock(InitJobQueuePublisher.class);
	private final InitializerProperties properties = new InitializerProperties();

	private InitJobReaper reaper;

	@BeforeEach
	void setUp() {
		properties.getJobs().setLeaseTimeoutSeconds(120);
		reaper = new InitJobReaper(repository, queuePublisher, properties);
		when(repository.requeueStaleRunning(any(Duration.class))).thenReturn(List.of());
		when(repository.failExhaustedStaleRunning(any(Duration.class))).thenReturn(0);
		when(repository.claimDueRetries()).thenReturn(List.of());
	}

	@Test
	void requeuesStaleRunningJobsBackOntoTheQueue() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(repository.requeueStaleRunning(any(Duration.class))).thenReturn(List.of(first, second));

		reaper.reap();

		verify(queuePublisher).enqueue(first);
		verify(queuePublisher).enqueue(second);
	}

	@Test
	void releasesDueRetriesToTheQueue() {
		UUID retry = UUID.randomUUID();
		when(repository.claimDueRetries()).thenReturn(List.of(retry));

		reaper.reap();

		verify(queuePublisher).enqueue(retry);
	}

	@Test
	void failsExhaustedStaleJobsWithoutEnqueueing() {
		when(repository.failExhaustedStaleRunning(any(Duration.class))).thenReturn(2);

		reaper.reap();

		verify(repository).failExhaustedStaleRunning(any(Duration.class));
		verify(queuePublisher, never()).enqueue(any());
	}

	@Test
	void swallowsRepositoryFailuresSoTheScheduleSurvives() {
		when(repository.requeueStaleRunning(any(Duration.class)))
				.thenThrow(new RuntimeException("db down"));

		assertThatCode(() -> reaper.reap()).doesNotThrowAnyException();
	}
}
