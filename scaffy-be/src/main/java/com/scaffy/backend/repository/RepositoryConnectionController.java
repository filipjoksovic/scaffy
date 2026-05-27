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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.ScaffyPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryConnectionController {

	private final RepositoryConnectionRepository repository;
	private final RepositoryAnalysisService repositoryAnalysisService;
	private final RepositoryAnalysisRepository repositoryAnalysisRepository;
	private final GitHubRepositoryClient gitHubRepositoryClient;
	private final GitHubRepositoryRefParser parser;

	public RepositoryConnectionController(
			RepositoryConnectionRepository repository,
			RepositoryAnalysisService repositoryAnalysisService,
			RepositoryAnalysisRepository repositoryAnalysisRepository,
			GitHubRepositoryClient gitHubRepositoryClient,
			GitHubRepositoryRefParser parser) {
		this.repository = repository;
		this.repositoryAnalysisService = repositoryAnalysisService;
		this.repositoryAnalysisRepository = repositoryAnalysisRepository;
		this.gitHubRepositoryClient = gitHubRepositoryClient;
		this.parser = parser;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RepositoryConnectionResponse> list(@AuthenticationPrincipal ScaffyPrincipal principal) {
		List<RepositoryConnection> connections = repository.findByUserId(principal.userId());
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
	public List<GitHubRepositoryResponse> listGitHubRepositories(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return gitHubRepositoryClient.findRepositories(principal.userId())
				.stream()
				.map(GitHubRepositoryResponse::from)
				.toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public RepositoryConnectionResponse connect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
		@Valid @RequestBody ConnectRepositoryRequest request) {
		GitHubRepositoryRef ref = parser.parse(request.repository());
		return RepositoryConnectionResponse.from(repository.connectGitHub(principal.userId(), ref), null, 0);
	}

	@PostMapping(path = "/{id}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisResponse analyzeRepository(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		return repositoryAnalysisService.analyze(principal.userId(), id);
	}

	@GetMapping(path = "/{id}/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisResponse getRepositoryAnalysis(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getStoredAnalysis(principal.userId(), id);
	}

	@GetMapping(path = "/{id}/analysis/runs", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RepositoryAnalysisRunSummaryResponse> getRepositoryAnalysisRuns(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getAnalysisRuns(principal.userId(), id)
				.stream()
				.map(RepositoryAnalysisRunSummaryResponse::from)
				.toList();
	}

	@GetMapping(path = "/{id}/analysis/delta", produces = MediaType.APPLICATION_JSON_VALUE)
	public RepositoryAnalysisDeltaResponse getRepositoryAnalysisDelta(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		return repositoryAnalysisService.getAnalysisDelta(principal.userId(), id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(@AuthenticationPrincipal ScaffyPrincipal principal, @PathVariable UUID id) {
		if (!repository.deleteForUser(principal.userId(), id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found.");
		}
	}

	public record RepositoryConnectionResponse(
			String id,
			String provider,
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
			String analyzerModelVersion) {

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
					summary.analyzerModelVersion());
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
			String analyzerModelVersion) {

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
					summary.analyzerModelVersion());
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
}
