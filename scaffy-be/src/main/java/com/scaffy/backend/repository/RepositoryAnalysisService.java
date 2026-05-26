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

	public RepositoryAnalysisService(
			GitHubWorkflowClient gitHubWorkflowClient,
			PipelineAnalyzer pipelineAnalyzer,
			RepositoryConnectionRepository repository) {
		this.gitHubWorkflowClient = gitHubWorkflowClient;
		this.pipelineAnalyzer = pipelineAnalyzer;
		this.repository = repository;
	}

	public RepositoryAnalysisResponse analyze(UUID userId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForUser(userId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found."));
		if (!"github".equals(connection.provider())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only GitHub repositories can be analyzed right now.");
		}
		GitHubWorkflowFile workflow = gitHubWorkflowClient.findWorkflow(userId, connection);
		AnalysisResponse analysis = pipelineAnalyzer.analyze(workflow.path(), workflow.content());
		return new RepositoryAnalysisResponse(
				connection.id().toString(),
				connection.owner() + "/" + connection.name(),
				workflow.path(),
				analysis);
	}
}
