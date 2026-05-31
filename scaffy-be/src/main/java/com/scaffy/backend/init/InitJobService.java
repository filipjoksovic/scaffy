package com.scaffy.backend.init;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InitJobService {

	private final StackValidator stackValidator;
	private final InitGenerationJobRepository repository;
	private final InitJobQueuePublisher queuePublisher;
	private final ObjectMapper objectMapper;
	private final InitializerProperties properties;

	public InitJobService(
			StackValidator stackValidator,
			InitGenerationJobRepository repository,
			InitJobQueuePublisher queuePublisher,
			ObjectMapper objectMapper,
			InitializerProperties properties) {
		this.stackValidator = stackValidator;
		this.repository = repository;
		this.queuePublisher = queuePublisher;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public InitJobResponse create(InitJobRequest request, UUID userId, String idempotencyKey) {
		InitSelection selection = stackValidator.validate(request);
		String key = normalizeIdempotencyKey(idempotencyKey);

		if (userId != null && key != null) {
			Optional<InitGenerationJob> existing = repository.findByUserIdAndIdempotencyKey(userId, key);
			if (existing.isPresent()) {
				return toResponse(existing.get(), readSelection(existing.get().selectionJson()));
			}
		}

		enforceConcurrencyLimit(userId);

		UUID id = UUID.randomUUID();
		InitGenerationJob job;
		try {
			job = repository.insert(
					id,
					userId,
					request,
					writeJson(request),
					writeJson(selection),
					userId == null ? null : key);
		} catch (DuplicateKeyException ex) {
			// A concurrent identical request won the idempotency-key race; return its job.
			return repository.findByUserIdAndIdempotencyKey(userId, key)
					.map(saved -> toResponse(saved, readSelection(saved.selectionJson())))
					.orElseThrow(() -> ex);
		}
		queuePublisher.enqueue(id);
		return toResponse(job, selection);
	}

	private void enforceConcurrencyLimit(UUID userId) {
		if (userId == null) {
			return;
		}
		int limit = properties.getJobs().getMaxInFlightPerUser();
		if (repository.countActiveByUser(userId) >= limit) {
			throw new InitJobLimitExceededException(
					"You already have " + limit + " generation jobs in progress. Wait for one to finish before starting another.");
		}
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return null;
		}
		String trimmed = idempotencyKey.trim();
		return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
	}

	public InitJobResponse get(UUID id) {
		InitGenerationJob job = repository.findById(id)
				.orElseThrow(() -> new InitJobNotFoundException(id));
		return toResponse(job, readSelection(job.selectionJson()));
	}

	public InitGenerationJob getJob(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new InitJobNotFoundException(id));
	}

	private InitJobResponse toResponse(InitGenerationJob job, InitSelection selection) {
		return new InitJobResponse(
				job.id(),
				job.status(),
				job.progressMessage(),
				job.errorMessage(),
				selection,
				"succeeded".equals(job.status()) && job.artifactObjectKey() != null,
				repository.findLogs(job.id(), 500),
				job.createdAt(),
				job.startedAt(),
				job.completedAt());
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize initializer job.", ex);
		}
	}

	private InitSelection readSelection(String json) {
		try {
			return objectMapper.readValue(json, InitSelection.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not read initializer job metadata.", ex);
		}
	}
}
