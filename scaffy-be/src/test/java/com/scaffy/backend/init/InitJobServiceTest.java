package com.scaffy.backend.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;

class InitJobServiceTest {

	private final StackValidator stackValidator = mock(StackValidator.class);
	private final InitGenerationJobRepository repository = mock(InitGenerationJobRepository.class);
	private final InitJobQueuePublisher queuePublisher = mock(InitJobQueuePublisher.class);
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final InitializerProperties properties = new InitializerProperties();

	private InitJobService service;
	private InitSelection selection;
	private InitJobRequest request;

	@BeforeEach
	void setUp() {
		properties.getJobs().setMaxInFlightPerUser(3);
		service = new InitJobService(stackValidator, repository, queuePublisher, objectMapper, properties);
		selection = sampleSelection();
		request = sampleRequest();
		when(stackValidator.validate(any(InitJobRequest.class))).thenReturn(selection);
		when(repository.findLogs(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
	}

	@Test
	void insertsAndEnqueuesWhenUnderLimit() {
		UUID userId = UUID.randomUUID();
		when(repository.countActiveByUser(userId)).thenReturn(0);
		when(repository.insert(any(), eq(userId), any(), anyString(), anyString(), isNull()))
				.thenAnswer(invocation -> jobWith(invocation.getArgument(0), userId, "queued"));

		InitJobResponse response = service.create(request, userId, null);

		assertThat(response.status()).isEqualTo("queued");
		verify(queuePublisher).enqueue(any());
	}

	@Test
	void rejectsWhenUserAtConcurrencyLimit() {
		UUID userId = UUID.randomUUID();
		when(repository.countActiveByUser(userId)).thenReturn(3);

		assertThatThrownBy(() -> service.create(request, userId, null))
				.isInstanceOf(InitJobLimitExceededException.class);

		verify(repository, never()).insert(any(), any(), any(), anyString(), anyString(), any());
		verify(queuePublisher, never()).enqueue(any());
	}

	@Test
	void returnsExistingJobWhenIdempotencyKeyMatches() {
		UUID userId = UUID.randomUUID();
		UUID existingId = UUID.randomUUID();
		when(repository.findByUserIdAndIdempotencyKey(userId, "abc"))
				.thenReturn(Optional.of(jobWith(existingId, userId, "running")));

		InitJobResponse response = service.create(request, userId, "abc");

		assertThat(response.jobId()).isEqualTo(existingId);
		assertThat(response.status()).isEqualTo("running");
		verify(repository, never()).countActiveByUser(any());
		verify(repository, never()).insert(any(), any(), any(), anyString(), anyString(), any());
		verify(queuePublisher, never()).enqueue(any());
	}

	@Test
	void returnsWinningJobWhenIdempotencyRaceHitsUniqueConstraint() {
		UUID userId = UUID.randomUUID();
		UUID winnerId = UUID.randomUUID();
		when(repository.findByUserIdAndIdempotencyKey(userId, "abc"))
				.thenReturn(Optional.empty(), Optional.of(jobWith(winnerId, userId, "queued")));
		when(repository.countActiveByUser(userId)).thenReturn(0);
		when(repository.insert(any(), eq(userId), any(), anyString(), anyString(), eq("abc")))
				.thenThrow(new DuplicateKeyException("duplicate idempotency key"));

		InitJobResponse response = service.create(request, userId, "abc");

		assertThat(response.jobId()).isEqualTo(winnerId);
		verify(queuePublisher, never()).enqueue(any());
	}

	@Test
	void anonymousRequestSkipsLimitAndIdempotency() {
		when(repository.insert(any(), isNull(), any(), anyString(), anyString(), isNull()))
				.thenAnswer(invocation -> jobWith(invocation.getArgument(0), null, "queued"));

		InitJobResponse response = service.create(request, null, "abc");

		assertThat(response.status()).isEqualTo("queued");
		verify(repository, never()).countActiveByUser(any());
		verify(repository, never()).findByUserIdAndIdempotencyKey(any(), anyString());
		verify(queuePublisher).enqueue(any());
	}

	private InitGenerationJob jobWith(UUID id, UUID userId, String status) {
		return new InitGenerationJob(
				id,
				userId,
				status,
				request.projectName(),
				writeJson(request),
				writeJson(selection),
				"Waiting for generator",
				null,
				null,
				OffsetDateTime.now(),
				null,
				null);
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static InitJobRequest sampleRequest() {
		return new InitJobRequest(
				"demo-app",
				"react",
				"19",
				"node-22",
				"spring-boot",
				"4.0",
				"java-21",
				"github-actions",
				"l2",
				true);
	}

	private static InitSelection sampleSelection() {
		return new InitSelection(
				new InitSelection.SelectedStack("react", "React", "react-19", "19", "19", "node-22", "Node 22", "node", "22"),
				new InitSelection.SelectedStack("spring-boot", "Spring Boot", "sb-4", "4.0", "4.0", "java-21", "Java 21", "java", "21"),
				new InitSelection.SelectedPipeline("github-actions", "GitHub Actions"),
				new InitSelection.SelectedMaturity("l2", "L2", "Standard", 2, false),
				true);
	}
}
