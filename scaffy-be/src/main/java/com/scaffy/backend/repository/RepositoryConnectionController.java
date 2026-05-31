package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.workspace.WorkspaceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryConnectionController {

	static final String WORKSPACE_HEADER = "X-Workspace-Id";

	private final RepositoryConnectionRepository repository;
	private final RepositoryAnalysisService repositoryAnalysisService;
	private final RepositoryAnalysisRepository repositoryAnalysisRepository;
	private final GitHubRepositoryClient gitHubRepositoryClient;
	private final GitLabRepositoryClient gitLabRepositoryClient;
	private final GitHubRepositoryRefParser parser;
	private final WorkspaceService workspaceService;

	public RepositoryConnectionController(
			RepositoryConnectionRepository repository,
			RepositoryAnalysisService repositoryAnalysisService,
			RepositoryAnalysisRepository repositoryAnalysisRepository,
			GitHubRepositoryClient gitHubRepositoryClient,
			GitLabRepositoryClient gitLabRepositoryClient,
			GitHubRepositoryRefParser parser,
			WorkspaceService workspaceService) {
		this.repository = repository;
		this.repositoryAnalysisService = repositoryAnalysisService;
		this.repositoryAnalysisRepository = repositoryAnalysisRepository;
		this.gitHubRepositoryClient = gitHubRepositoryClient;
		this.gitLabRepositoryClient = gitLabRepositoryClient;
		this.parser = parser;
		this.workspaceService = workspaceService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RepositoryConnectionResponse> list(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		List<RepositoryConnection> connections = repository.findByWorkspaceId(activeWorkspace);
		Map<UUID, RepositoryAnalysisSummary> summaries = repositoryAnalysisRepository.findLatestSummariesByRepositoryConnectionIds(
				connections.stream().map(RepositoryConnection::id).toList());
		Map<UUID, Integer> runCounts = repositoryAnalysisRepository.countByRepositoryConnectionIds(
				connections.stream().map(RepositoryConnection::id).toList());
		return connections
				.stream()
				.map(connection -> RepositoryConnectionResponse.from(
						connection,
						summaries.get(connection.id()),
						runCounts.getOrDefault(connection.id(), 0)))
				.toList();
	}

	@GetMapping(path = "/github", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<GitHubRepositoryResponse> listGitHubRepositories(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		return gitHubRepositoryClient.findRepositories(activeWorkspace, principal.userId())
				.stream()
				.map(GitHubRepositoryResponse::from)
				.toList();
	}

	@GetMapping(path = "/gitlab", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<GitLabRepositoryResponse> listGitLabRepositories(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@RequestParam(value = "instance", required = false) String instance) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		return gitLabRepositoryClient.findProjects(activeWorkspace, principal.userId(), instance)
				.stream()
				.map(GitLabRepositoryResponse::from)
				.toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public RepositoryConnectionResponse connect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@Valid @RequestBody ConnectRepositoryRequest request) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		String provider = request.providerOrDefault();
		RepositoryConnection connection;
		if ("gitlab".equals(provider)) {
			connection = connectGitLab(activeWorkspace, principal.userId(), request);
		}
		else if ("github".equals(provider)) {
			GitHubRepositoryRef ref = parser.parse(request.repository());
			connection = repository.connect(
					activeWorkspace, principal.userId(), "github", "", ref.owner(), ref.name(), ref.url());
		}
		else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider: " + provider);
		}
		return RepositoryConnectionResponse.from(connection, null, 0);
	}

	private RepositoryConnection connectGitLab(UUID workspaceId, UUID userId, ConnectRepositoryRequest request) {
		String host = request.instanceOrEmpty();
		if (host.isBlank()) {
			host = "gitlab.com";
		}
		String baseUrl = gitLabRepositoryClient.resolveBaseUrl(host)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown GitLab instance."));
		String trimmed = request.repository().trim();
		int start = 0;
		int end = trimmed.length();
		while (start < end && trimmed.charAt(start) == '/') {
			start++;
		}
		while (end > start && trimmed.charAt(end - 1) == '/') {
			end--;
		}
		String path = trimmed.substring(start, end);
		if (path.isBlank() || !path.contains("/")) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Enter a GitLab project as group/project path.");
		}
		String owner = path.substring(0, path.lastIndexOf('/'));
		String name = path.substring(path.lastIndexOf('/') + 1);
		String url = baseUrl + "/" + path;
		return repository.connect(workspaceId, userId, "gitlab", host, owner, name, url);
	}

	@PostMapping(path = "/{id}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisResponse analyzeRepository(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable UUID id) {
		return repositoryAnalysisService.analyze(
				workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId), id);
	}

	@GetMapping(path = "/{id}/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisResponse getRepositoryAnalysis(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getStoredAnalysis(
				workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId), id);
	}

	@GetMapping(path = "/{id}/analysis/runs", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RepositoryAnalysisRunSummaryResponse> getRepositoryAnalysisRuns(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getAnalysisRuns(
				workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId), id)
				.stream()
				.map(RepositoryAnalysisRunSummaryResponse::from)
				.toList();
	}

	@GetMapping(path = "/{id}/analysis/delta", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisDeltaResponse getRepositoryAnalysisDelta(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getAnalysisDelta(
				workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId), id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable UUID id) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		if (!repository.deleteForWorkspace(activeWorkspace, id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found.");
		}
	}

	public record RepositoryConnectionResponse(
			String id,
			String provider,
			String instance,
			String owner,
			String name,
			String url,
			OffsetDateTime connectedAt,
			int analysisRunCount,
			RepositoryAnalysisSummaryResponse analysisSummary) {

		static RepositoryConnectionResponse from(
				RepositoryConnection connection,
				RepositoryAnalysisSummary summary,
				int analysisRunCount) {
			return new RepositoryConnectionResponse(
					connection.id().toString(),
					connection.provider(),
					connection.providerInstance(),
					connection.owner(),
					connection.name(),
					connection.url(),
					connection.connectedAt(),
					analysisRunCount,
					RepositoryAnalysisSummaryResponse.from(summary));
		}
	}

	public record RepositoryAnalysisSummaryResponse(
			String runId,
			int runNumber,
			OffsetDateTime analyzedAt,
			String workflowPath,
			String workflowContentHash,
			double overallScore,
			int overallLevel,
			String overallStatus,
			int analysisSchemaVersion,
			String analyzerModelVersion,
			String status,
			String errorMessage) {

		static RepositoryAnalysisSummaryResponse from(RepositoryAnalysisSummary summary) {
			if (summary == null) {
				return null;
			}
			return new RepositoryAnalysisSummaryResponse(
					summary.id().toString(),
					summary.runNumber(),
					summary.analyzedAt(),
					summary.workflowPath(),
					summary.workflowContentHash(),
					summary.overallScore(),
					summary.overallLevel(),
					summary.overallStatus(),
					summary.analysisSchemaVersion(),
					summary.analyzerModelVersion(),
					summary.status(),
					summary.errorMessage());
		}
	}

	public record RepositoryAnalysisRunSummaryResponse(
			String runId,
			int runNumber,
			OffsetDateTime analyzedAt,
			String workflowPath,
			String workflowContentHash,
			double overallScore,
			int overallLevel,
			String overallStatus,
			int analysisSchemaVersion,
			String analyzerModelVersion,
			String status,
			String errorMessage) {

		static RepositoryAnalysisRunSummaryResponse from(RepositoryAnalysisSummary summary) {
			return new RepositoryAnalysisRunSummaryResponse(
					summary.id().toString(),
					summary.runNumber(),
					summary.analyzedAt(),
					summary.workflowPath(),
					summary.workflowContentHash(),
					summary.overallScore(),
					summary.overallLevel(),
					summary.overallStatus(),
					summary.analysisSchemaVersion(),
					summary.analyzerModelVersion(),
					summary.status(),
					summary.errorMessage());
		}
	}

	public record GitHubRepositoryResponse(
			String fullName,
			String owner,
			String name,
			String url,
			boolean privateRepository) {

		static GitHubRepositoryResponse from(GitHubRepositoryOption repository) {
			return new GitHubRepositoryResponse(
					repository.fullName(),
					repository.owner(),
					repository.name(),
					repository.url(),
					repository.privateRepository());
		}
	}

	public record GitLabRepositoryResponse(
			String fullName,
			String owner,
			String name,
			String url,
			boolean privateRepository) {

		static GitLabRepositoryResponse from(GitLabProjectOption project) {
			return new GitLabRepositoryResponse(
					project.pathWithNamespace(),
					project.owner(),
					project.name(),
					project.url(),
					project.privateRepository());
		}
	}
}
