package com.scaffy.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.notification.AppNotificationRepository;

@Service
public class RepositoryAnalysisJobService {

	private static final int LOG_LIMIT = 500;

	private final RepositoryConnectionRepository connectionRepository;
	private final RepositoryAnalysisJobRepository jobRepository;
	private final RepositoryAnalysisQueuePublisher queuePublisher;
	private final RepositoryAnalysisProperties properties;
	private final RepositoryAnalysisService analysisService;
	private final AppNotificationRepository notificationRepository;

	public RepositoryAnalysisJobService(
			RepositoryConnectionRepository connectionRepository,
			RepositoryAnalysisJobRepository jobRepository,
			RepositoryAnalysisQueuePublisher queuePublisher,
			RepositoryAnalysisProperties properties,
			RepositoryAnalysisService analysisService,
			AppNotificationRepository notificationRepository) {
		this.connectionRepository = connectionRepository;
		this.jobRepository = jobRepository;
		this.queuePublisher = queuePublisher;
		this.properties = properties;
		this.analysisService = analysisService;
		this.notificationRepository = notificationRepository;
	}

	public RepositoryAnalysisJobResponse createOrGetActive(UUID workspaceId, UUID userId, UUID repositoryId) {
		RepositoryConnection connection = connectionRepository.findByIdForWorkspace(workspaceId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found."));
		RepositoryAnalysisJob job = jobRepository.findActiveForRepository(connection.id())
				.orElseGet(() -> create(connection, userId));
		return toResponse(job);
	}

	private RepositoryAnalysisJob create(RepositoryConnection connection, UUID userId) {
		UUID id = UUID.randomUUID();
		RepositoryAnalysisJob job = jobRepository.create(
				id,
				connection.workspaceId(),
				userId,
				connection.id(),
				properties.getJobs().getMaxAttempts());
		queuePublisher.enqueue(id);
		return job;
	}

	public RepositoryAnalysisJobResponse get(UUID userId, UUID jobId) {
		RepositoryAnalysisJob job = jobRepository.findByIdForUser(userId, jobId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis job not found."));
		return toResponse(job);
	}

	public List<RepositoryAnalysisJobResponse> active(UUID userId) {
		return jobRepository.findActiveByUser(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	public void execute(UUID jobId) {
		if (!jobRepository.claim(jobId)) {
			return;
		}
		RepositoryAnalysisJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis job not found."));
		RepositoryConnection connection = connectionRepository
				.findByIdForWorkspace(job.workspaceId(), job.repositoryConnectionId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found."));
		try {
			jobRepository.progress(jobId, 12, "Analyzer claimed the job");
			PersistedRepositoryAnalysis analysis = analysisService.runAndPersistForJob(
					connection,
					(percent, message) -> jobRepository.progress(jobId, percent, message));
			jobRepository.succeed(jobId, analysis.summary().id());
			notificationRepository.create(
					job.userId(),
					job.workspaceId(),
					"repository_analysis_succeeded",
					"Repository analysis complete",
					connection.owner() + "/" + connection.name() + " has been analyzed.",
					"/dashboard?repository=" + connection.id());
		}
		catch (RuntimeException ex) {
			String message = failureReason(ex);
			jobRepository.fail(jobId, message, properties.getJobs().getRetryBackoffMs());
			RepositoryAnalysisJob updated = jobRepository.findById(jobId).orElse(job);
			if ("failed".equals(updated.status())) {
				notificationRepository.create(
						job.userId(),
						job.workspaceId(),
						"repository_analysis_failed",
						"Repository analysis failed",
						connection.owner() + "/" + connection.name() + ": " + updated.errorMessage(),
						"/dashboard?repository=" + connection.id());
			}
			throw ex;
		}
	}

	public void heartbeat(UUID jobId) {
		jobRepository.heartbeat(jobId);
	}

	private RepositoryAnalysisJobResponse toResponse(RepositoryAnalysisJob job) {
		return RepositoryAnalysisJobResponse.from(job, jobRepository.findLogs(job.id(), LOG_LIMIT));
	}

	private String failureReason(RuntimeException ex) {
		if (ex instanceof ResponseStatusException responseStatusException
				&& responseStatusException.getReason() != null
				&& !responseStatusException.getReason().isBlank()) {
			return responseStatusException.getReason();
		}
		if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
			return ex.getMessage();
		}
		return ex.getClass().getSimpleName();
	}
}
