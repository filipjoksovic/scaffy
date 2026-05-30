package com.scaffy.backend.repository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.OAuthAccessTokenRecord;
import com.scaffy.backend.auth.UserRepository;
import com.scaffy.backend.init.InitGenerationJob;
import com.scaffy.backend.init.InitGenerationJobRepository;
import com.scaffy.backend.init.InitJobNotFoundException;

@Service
public class RepositoryPublicationService {

	private final InitGenerationJobRepository initGenerationJobRepository;
	private final RepositoryPublicationJobRepository publicationJobRepository;
	private final RepositoryConnectionRepository connectionRepository;
	private final RepositoryPublicationQueuePublisher queuePublisher;
	private final UserRepository userRepository;

	public RepositoryPublicationService(
			InitGenerationJobRepository initGenerationJobRepository,
			RepositoryPublicationJobRepository publicationJobRepository,
			RepositoryConnectionRepository connectionRepository,
			RepositoryPublicationQueuePublisher queuePublisher,
			UserRepository userRepository) {
		this.initGenerationJobRepository = initGenerationJobRepository;
		this.publicationJobRepository = publicationJobRepository;
		this.connectionRepository = connectionRepository;
		this.queuePublisher = queuePublisher;
		this.userRepository = userRepository;
	}

	public RepositoryPublicationResponse create(UUID userId, UUID workspaceId, CreateRepositoryPublicationRequest request) {
		InitGenerationJob initJob = initGenerationJobRepository.findById(request.initJobId())
				.orElseThrow(() -> new InitJobNotFoundException(request.initJobId()));
		if (initJob.userId() == null || !initJob.userId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Initializer job not found.");
		}
		if (!"succeeded".equals(initJob.status()) || initJob.artifactObjectKey() == null) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Initializer job must finish successfully before publishing to GitHub.");
		}

		OAuthAccessTokenRecord token = userRepository.findOAuthAccessToken(userId, "github")
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.CONFLICT,
						"Reconnect with GitHub before creating repositories."));
		Set<String> scopes = scopes(token);
		if (!scopes.contains("repo") || !scopes.contains("workflow")) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Reconnect GitHub with repository and workflow access before creating repositories.");
		}

		UUID id = UUID.randomUUID();
		RepositoryPublicationJob job = publicationJobRepository.insert(
				id,
				userId,
				workspaceId,
				request.initJobId(),
				request.repositoryName().trim(),
				request.description());
		queuePublisher.enqueue(id);
		return toResponse(job);
	}

	public RepositoryPublicationResponse get(UUID userId, UUID id) {
		RepositoryPublicationJob job = publicationJobRepository.findByIdForUser(userId, id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication job not found."));
		return toResponse(job);
	}

	private RepositoryPublicationResponse toResponse(RepositoryPublicationJob job) {
		RepositoryConnectionController.RepositoryConnectionResponse connection = null;
		if (job.repositoryConnectionId() != null && job.workspaceId() != null) {
			connection = connectionRepository.findByIdForWorkspace(job.workspaceId(), job.repositoryConnectionId())
					.map(repositoryConnection -> RepositoryConnectionController.RepositoryConnectionResponse.from(
							repositoryConnection,
							null,
							0))
					.orElse(null);
		}
		return new RepositoryPublicationResponse(
				job.id(),
				job.status(),
				job.progressMessage(),
				job.errorMessage(),
				job.provider(),
				job.repositoryName(),
				job.visibility(),
				job.repositoryOwner(),
				job.repositoryUrl(),
				connection,
				publicationJobRepository.findLogs(job.id(), 500),
				job.createdAt(),
				job.startedAt(),
				job.completedAt());
	}

	private Set<String> scopes(OAuthAccessTokenRecord token) {
		if (token.scopes() == null || token.scopes().isBlank()) {
			return Set.of();
		}
		return Arrays.stream(token.scopes().split("[,\\s]+"))
				.map(scope -> scope.toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
	}
}
