package com.scaffy.backend.repository;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.PipelineAnalyzer;

@Service
public class RepositoryAnalysisService {

	private final GitHubWorkflowClient gitHubWorkflowClient;
	private final PipelineAnalyzer pipelineAnalyzer;
	private final RepositoryConnectionRepository repository;
	private final RepositoryAnalysisRepository analysisRepository;

	public RepositoryAnalysisService(
			GitHubWorkflowClient gitHubWorkflowClient,
			PipelineAnalyzer pipelineAnalyzer,
			RepositoryConnectionRepository repository,
			RepositoryAnalysisRepository analysisRepository) {
		this.gitHubWorkflowClient = gitHubWorkflowClient;
		this.pipelineAnalyzer = pipelineAnalyzer;
		this.repository = repository;
		this.analysisRepository = analysisRepository;
	}

	public RepositoryAnalysisResponse analyze(UUID userId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForUser(userId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found."));
		return analysisRepository.findByRepositoryConnectionId(connection.id())
				.map(persisted -> RepositoryAnalysisResponse.from(connection, persisted))
				.orElseGet(() -> runAndPersist(connection, userId));
	}

	public RepositoryAnalysisResponse getStoredAnalysis(UUID userId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForUser(userId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found."));
		return analysisRepository.findByRepositoryConnectionId(connection.id())
				.map(persisted -> RepositoryAnalysisResponse.from(connection, persisted))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository analysis not found."));
	}

	private RepositoryAnalysisResponse runAndPersist(RepositoryConnection connection, UUID userId) {
		if (!"github".equals(connection.provider())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only GitHub repositories can be analyzed right now.");
		}
		GitHubWorkflowFile workflow = gitHubWorkflowClient.findWorkflow(userId, connection);
		AnalysisResponse analysis = pipelineAnalyzer.analyze(workflow.path(), workflow.content());
		PersistedRepositoryAnalysis persisted = analysisRepository.insert(connection.id(), workflow.path(), analysis);
		return RepositoryAnalysisResponse.from(connection, persisted);
	}
}
