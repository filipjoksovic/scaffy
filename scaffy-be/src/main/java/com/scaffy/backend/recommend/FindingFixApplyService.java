package com.scaffy.backend.recommend;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.repository.GitHubWorkflowClient;
import com.scaffy.backend.repository.GitLabWorkflowClient;
import com.scaffy.backend.repository.RepositoryAnalysisRepository;
import com.scaffy.backend.repository.RepositoryConnection;
import com.scaffy.backend.repository.RepositoryConnectionRepository;
import com.scaffy.backend.repository.WorkflowCommitResult;

@Service
public class FindingFixApplyService {

	private static final Logger log = LoggerFactory.getLogger(FindingFixApplyService.class);
	private static final String DEFAULT_COMMIT_MESSAGE = "Improve CI/CD pipeline quality";

	private final FindingFixRepository findingFixRepository;
	private final RepositoryAnalysisRepository analysisRepository;
	private final RepositoryConnectionRepository connectionRepository;
	private final GitHubWorkflowClient gitHubWorkflowClient;
	private final GitLabWorkflowClient gitLabWorkflowClient;

	public FindingFixApplyService(
			FindingFixRepository findingFixRepository,
			RepositoryAnalysisRepository analysisRepository,
			RepositoryConnectionRepository connectionRepository,
			GitHubWorkflowClient gitHubWorkflowClient,
			GitLabWorkflowClient gitLabWorkflowClient) {
		this.findingFixRepository = findingFixRepository;
		this.analysisRepository = analysisRepository;
		this.connectionRepository = connectionRepository;
		this.gitHubWorkflowClient = gitHubWorkflowClient;
		this.gitLabWorkflowClient = gitLabWorkflowClient;
	}

	public FindingFixApplyResponse apply(UUID userId, UUID workspaceId, FindingFixApplyRequest request) {
		if (request == null
				|| request.analysisRunId() == null
				|| request.finding() == null
				|| isBlank(request.workflowPath())
				|| isBlank(request.workflowContent())) {
			return FindingFixApplyResponse.error("analysisRunId, finding, workflowPath and workflowContent are required");
		}

		String findingHash = FindingHasher.hash(request.finding());

		Optional<UUID> connectionId = analysisRepository.findRunConnectionId(request.analysisRunId());
		if (connectionId.isEmpty()) {
			return FindingFixApplyResponse.error("Unknown analysis run");
		}

		RepositoryConnection connection = connectionRepository
				.findByIdForWorkspace(workspaceId, connectionId.get())
				.orElse(null);
		if (connection == null) {
			return FindingFixApplyResponse.unavailable(
					"Connect a repository in this workspace before committing AI suggestions.");
		}

		String commitMessage = isBlank(request.commitMessage()) ? DEFAULT_COMMIT_MESSAGE : request.commitMessage().trim();

		WorkflowCommitResult result;
		try {
			result = commit(userId, workspaceId, connection, request, commitMessage);
		}
		catch (ResponseStatusException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.CONFLICT) {
				return FindingFixApplyResponse.unavailable(ex.getReason());
			}
			log.warn("Commit failed for run={} hash={} status={}: {}",
					request.analysisRunId(), findingHash, ex.getStatusCode(), ex.getReason());
			return FindingFixApplyResponse.error(ex.getReason() == null ? "Commit failed" : ex.getReason());
		}

		try {
			findingFixRepository.markCommitted(
					request.analysisRunId(),
					findingHash,
					result.commitSha(),
					result.commitUrl(),
					result.branch());
		}
		catch (org.springframework.dao.DataAccessException ex) {
			log.warn("Could not persist commit metadata for run={} hash={}: {}",
					request.analysisRunId(), findingHash, ex.getMessage());
		}

		return FindingFixApplyResponse.ok(result.commitSha(), result.commitUrl(), result.branch());
	}

	private WorkflowCommitResult commit(
			UUID userId,
			UUID workspaceId,
			RepositoryConnection connection,
			FindingFixApplyRequest request,
			String commitMessage) {
		String provider = connection.provider();
		if ("github".equalsIgnoreCase(provider)) {
			return gitHubWorkflowClient.commitWorkflow(
					workspaceId, userId, connection, request.workflowPath(), request.workflowContent(), commitMessage);
		}
		if ("gitlab".equalsIgnoreCase(provider)) {
			return gitLabWorkflowClient.commitWorkflow(
					workspaceId, userId, connection, request.workflowPath(), request.workflowContent(), commitMessage);
		}
		throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"Commits are not supported for provider: " + provider);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
