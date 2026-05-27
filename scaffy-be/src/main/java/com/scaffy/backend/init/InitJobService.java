package com.scaffy.backend.init;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InitJobService {

	private final StackValidator stackValidator;
	private final InitGenerationJobRepository repository;
	private final InitJobQueuePublisher queuePublisher;
	private final ObjectMapper objectMapper;

	public InitJobService(
			StackValidator stackValidator,
			InitGenerationJobRepository repository,
			InitJobQueuePublisher queuePublisher,
			ObjectMapper objectMapper) {
		this.stackValidator = stackValidator;
		this.repository = repository;
		this.queuePublisher = queuePublisher;
		this.objectMapper = objectMapper;
	}

	public InitJobResponse create(InitJobRequest request, UUID userId) {
		InitSelection selection = stackValidator.validate(request);
		UUID id = UUID.randomUUID();
		InitGenerationJob job = repository.insert(
				id,
				userId,
				request,
				writeJson(request),
				writeJson(selection));
		queuePublisher.enqueue(id);
		return toResponse(job, selection);
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
