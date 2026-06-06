package com.scaffy.backend.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.MetricsStatus;
import com.scaffy.backend.repository.metrics.WorkflowMetricsProvider;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.CapabilityScore;
import com.scaffy.backend.analyze.DomainScore;
import com.scaffy.backend.analyze.FindingType;
import com.scaffy.backend.analyze.PipelineAnalyzer;
import com.scaffy.backend.analyze.PipelineProvider;

@Service
public class RepositoryAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisService.class);
	private static final String CONNECTION_NOT_FOUND = "Repository connection not found.";
	private static final int METRICS_WINDOW_DAYS = 30;

	private final GitHubWorkflowClient gitHubWorkflowClient;
	private final GitLabWorkflowClient gitLabWorkflowClient;
	private final PipelineAnalyzer pipelineAnalyzer;
	private final RepositoryConnectionRepository repository;
	private final RepositoryAnalysisRepository analysisRepository;
	private final Map<String, WorkflowMetricsProvider> metricsProviders;
	private final com.scaffy.backend.analyze.ScoringEngine scoringEngine;

	public RepositoryAnalysisService(
			GitHubWorkflowClient gitHubWorkflowClient,
			GitLabWorkflowClient gitLabWorkflowClient,
			PipelineAnalyzer pipelineAnalyzer,
			RepositoryConnectionRepository repository,
			RepositoryAnalysisRepository analysisRepository,
			@Qualifier("cachedMetricsProvidersByName") Map<String, WorkflowMetricsProvider> metricsProviders,
			com.scaffy.backend.analyze.ScoringEngine scoringEngine) {
		this.gitHubWorkflowClient = gitHubWorkflowClient;
		this.gitLabWorkflowClient = gitLabWorkflowClient;
		this.pipelineAnalyzer = pipelineAnalyzer;
		this.repository = repository;
		this.analysisRepository = analysisRepository;
		this.metricsProviders = metricsProviders;
		this.scoringEngine = scoringEngine;
	}

	public RepositoryAnalysisResponse analyze(UUID workspaceId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForWorkspace(workspaceId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CONNECTION_NOT_FOUND));
		try {
			return runAndPersist(connection);
		}
		catch (ResponseStatusException ex) {
			analysisRepository.insertFailure(connection.id(), failureReason(ex.getReason(), ex));
			throw ex;
		}
		catch (RuntimeException ex) {
			analysisRepository.insertFailure(connection.id(), failureReason(ex.getMessage(), ex));
			throw ex;
		}
	}

	private String failureReason(String message, RuntimeException ex) {
		if (message != null && !message.isBlank()) {
			return message;
		}
		return ex.getClass().getSimpleName();
	}

	public RepositoryAnalysisResponse getStoredAnalysis(UUID workspaceId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForWorkspace(workspaceId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CONNECTION_NOT_FOUND));
		return analysisRepository.findLatestByRepositoryConnectionId(connection.id())
				.map(persisted -> RepositoryAnalysisResponse.from(connection, persisted))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository analysis not found."));
	}

	public List<RepositoryAnalysisSummary> getAnalysisRuns(UUID workspaceId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForWorkspace(workspaceId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CONNECTION_NOT_FOUND));
		return analysisRepository.findSummariesByRepositoryConnectionId(connection.id());
	}

	public RepositoryAnalysisDeltaResponse getAnalysisDelta(UUID workspaceId, UUID repositoryId) {
		RepositoryConnection connection = repository.findByIdForWorkspace(workspaceId, repositoryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CONNECTION_NOT_FOUND));
		List<PersistedRepositoryAnalysis> latestPair = analysisRepository.findLatestPairByRepositoryConnectionId(connection.id());
		if (latestPair.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository analysis not found.");
		}
		PersistedRepositoryAnalysis current = latestPair.get(0);
		if (latestPair.size() == 1) {
			return new RepositoryAnalysisDeltaResponse(
					false,
					null,
					RepositoryAnalysisDeltaResponse.RunSummary.from(current.summary()),
					null,
					List.of(),
					List.of(),
					List.of());
		}
		PersistedRepositoryAnalysis base = latestPair.get(1);
		return compare(base, current);
	}

	private RepositoryAnalysisResponse runAndPersist(RepositoryConnection connection) {
		List<WorkflowSource> workflows = fetchWorkflows(connection);
		List<WorkflowSource> successfulWorkflows = new ArrayList<>();
		List<WorkflowAnalysisItem> workflowAnalyses = new ArrayList<>();
		for (WorkflowSource workflow : workflows) {
			try {
				AnalysisResponse analysis = pipelineAnalyzer.analyze(workflow.path(), workflow.content());
				WorkflowMetricsResult metricsResult = fetchMetricsSafely(connection, workflow.path());
				workflowAnalyses.add(WorkflowAnalysisItem.success(workflow.path(), analysis, metricsResult));
				successfulWorkflows.add(workflow);
			}
			catch (RuntimeException ex) {
				log.warn("Workflow analysis failed for {}/{} path={}: {}",
						connection.owner(), connection.name(), workflow.path(), ex.getMessage());
				workflowAnalyses.add(WorkflowAnalysisItem.failure(workflow.path(), failureReason(ex.getMessage(), ex)));
			}
		}

		List<WorkflowAnalysisItem> successfulAnalyses = workflowAnalyses.stream()
				.filter(WorkflowAnalysisItem::succeeded)
				.toList();
		if (successfulAnalyses.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"Workflow files were found, but none could be analyzed successfully.");
		}

		AnalysisResponse aggregatedAnalysis = aggregateAnalysis(successfulAnalyses);
		WorkflowSource primaryWorkflow = successfulWorkflows.get(0);
		WorkflowMetricsResult primaryMetrics = successfulAnalyses.get(0).workflowMetrics();
		PersistedAnalysisBlob blob = PersistedAnalysisBlob.of(aggregatedAnalysis, primaryMetrics, workflowAnalyses);
		PersistedRepositoryAnalysis persisted = analysisRepository.insert(
				connection.id(), primaryWorkflow.path(), primaryWorkflow.content(), blob);
		return RepositoryAnalysisResponse.from(connection, persisted);
	}

	private AnalysisResponse aggregateAnalysis(List<WorkflowAnalysisItem> successfulAnalyses) {
		PipelineProvider provider = successfulAnalyses.get(0).analysis().provider();

		Map<String, List<CapabilityFinding>> findingsByDimension = new LinkedHashMap<>();
		for (WorkflowAnalysisItem workflow : successfulAnalyses) {
			for (DomainScore dimension : workflow.analysis().dimensions()) {
				List<CapabilityFinding> findings = dimension.capabilityScores().stream()
						.flatMap(capability -> capability.findings().stream())
						.toList();
				findingsByDimension.computeIfAbsent(dimension.dimension(), ignored -> new ArrayList<>())
						.addAll(findings);
			}
		}

		List<DomainScore> domainScores = findingsByDimension.entrySet().stream()
				.map(entry -> scoringEngine.score(entry.getKey(), entry.getValue()))
				.toList();
		List<CapabilityFinding> allFindings = findingsByDimension.values().stream()
				.flatMap(List::stream)
				.toList();
		double overallScore = scoringEngine.overallScore(domainScores);
		int overallLevel = scoringEngine.maturityLevel(overallScore, domainScores, allFindings);
		return new AnalysisResponse(
				provider,
				overallScore,
				overallLevel,
				scoringEngine.overallStatus(overallScore, domainScores),
				domainScores);
	}

	private WorkflowMetricsResult fetchMetricsSafely(RepositoryConnection connection, String workflowFile) {
		String metricsProviderName = switch (connection.provider()) {
			case "github" -> "github-actions";
			default -> null;
		};
		if (metricsProviderName == null) {
			return WorkflowMetricsResult.unsupported();
		}
		WorkflowMetricsProvider provider = metricsProviders.get(metricsProviderName);
		if (provider == null) {
			return WorkflowMetricsResult.unsupported();
		}
		MetricsRequest request = new MetricsRequest(
				connection.workspaceId(),
				connection.userId(),
				metricsProviderName,
				connection.providerInstance() != null ? connection.providerInstance() : "",
				connection.owner(),
				connection.name(),
				workflowFile,
				METRICS_WINDOW_DAYS);
		try {
			return provider.fetchMetrics(request);
		}
		catch (Exception ex) {
			log.warn("Metrics fetch failed unexpectedly for {}/{}: {}",
					connection.owner(), connection.name(), ex.getMessage());
			return WorkflowMetricsResult.unavailable(
					MetricsStatus.PROVIDER_ERROR,
					"Unexpected error during metrics fetch");
		}
	}

	private List<WorkflowSource> fetchWorkflows(RepositoryConnection connection) {
		return switch (connection.provider()) {
			case "github" -> {
				List<GitHubWorkflowFile> files = gitHubWorkflowClient.findWorkflows(
						connection.workspaceId(), connection.userId(), connection);
				yield files.stream().map(file -> new WorkflowSource(file.path(), file.content())).toList();
			}
			case "gitlab" -> {
				GitLabCiFile file = gitLabWorkflowClient.findCiFile(
						connection.workspaceId(), connection.userId(), connection);
				yield List.of(new WorkflowSource(file.path(), file.content()));
			}
			default -> throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Analysis is not supported for provider: " + connection.provider());
		};
	}

	private record WorkflowSource(String path, String content) {
	}

	private RepositoryAnalysisDeltaResponse compare(PersistedRepositoryAnalysis base, PersistedRepositoryAnalysis current) {
		return new RepositoryAnalysisDeltaResponse(
				true,
				RepositoryAnalysisDeltaResponse.RunSummary.from(base.summary()),
				RepositoryAnalysisDeltaResponse.RunSummary.from(current.summary()),
				overallDelta(base.analysis(), current.analysis()),
				dimensionDeltas(base.analysis(), current.analysis()),
				capabilityDeltas(base.analysis(), current.analysis()),
				findingChanges(base.analysis(), current.analysis()));
	}

	private RepositoryAnalysisDeltaResponse.ScoreDelta overallDelta(AnalysisResponse base, AnalysisResponse current) {
		double scoreDelta = current.overallScore() - base.overallScore();
		int levelDelta = current.overallLevel() - base.overallLevel();
		return new RepositoryAnalysisDeltaResponse.ScoreDelta(
				base.overallScore(),
				current.overallScore(),
				scoreDelta,
				base.overallLevel(),
				current.overallLevel(),
				levelDelta,
				base.overallStatus().value(),
				current.overallStatus().value(),
				direction(scoreDelta, levelDelta, base.overallStatus(), current.overallStatus()));
	}

	private List<RepositoryAnalysisDeltaResponse.DimensionDelta> dimensionDeltas(
			AnalysisResponse base,
			AnalysisResponse current) {
		Map<String, DomainScore> baseDimensions = analysisByDimension(base);
		Map<String, DomainScore> currentDimensions = analysisByDimension(current);
		return orderedKeys(baseDimensions, currentDimensions)
				.stream()
				.map(dimension -> dimensionDelta(dimension, baseDimensions.get(dimension), currentDimensions.get(dimension)))
				.toList();
	}

	private RepositoryAnalysisDeltaResponse.DimensionDelta dimensionDelta(
			String dimension,
			DomainScore base,
			DomainScore current) {
		double baseScore = base == null ? 0 : base.score();
		double currentScore = current == null ? 0 : current.score();
		int baseLevel = base == null ? 0 : base.level();
		int currentLevel = current == null ? 0 : current.level();
		AnalysisStatus baseStatus = base == null ? AnalysisStatus.NOT_EVALUATED : base.status();
		AnalysisStatus currentStatus = current == null ? AnalysisStatus.NOT_EVALUATED : current.status();
		return new RepositoryAnalysisDeltaResponse.DimensionDelta(
				dimension,
				baseScore,
				currentScore,
				currentScore - baseScore,
				baseLevel,
				currentLevel,
				currentLevel - baseLevel,
				baseStatus.value(),
				currentStatus.value(),
				direction(currentScore - baseScore, currentLevel - baseLevel, baseStatus, currentStatus));
	}

	private List<RepositoryAnalysisDeltaResponse.CapabilityDelta> capabilityDeltas(
			AnalysisResponse base,
			AnalysisResponse current) {
		Map<CapabilityKey, CapabilityScore> baseCapabilities = capabilitiesByKey(base);
		Map<CapabilityKey, CapabilityScore> currentCapabilities = capabilitiesByKey(current);
		return orderedKeys(baseCapabilities, currentCapabilities)
				.stream()
				.map(key -> capabilityDelta(key, baseCapabilities.get(key), currentCapabilities.get(key)))
				.toList();
	}

	private RepositoryAnalysisDeltaResponse.CapabilityDelta capabilityDelta(
			CapabilityKey key,
			CapabilityScore base,
			CapabilityScore current) {
		int basePoints = base == null ? 0 : base.points();
		int currentPoints = current == null ? 0 : current.points();
		int baseFindingCount = base == null ? 0 : base.findings().size();
		int currentFindingCount = current == null ? 0 : current.findings().size();
		return new RepositoryAnalysisDeltaResponse.CapabilityDelta(
				key.dimension(),
				key.capability(),
				basePoints,
				currentPoints,
				currentPoints - basePoints,
				baseFindingCount,
				currentFindingCount,
				currentFindingCount - baseFindingCount,
				direction(currentPoints - basePoints, 0, null, null));
	}

	private List<RepositoryAnalysisDeltaResponse.FindingChange> findingChanges(
			AnalysisResponse base,
			AnalysisResponse current) {
		Map<FindingKey, CapabilityFinding> baseFindings = findingsByKey(base);
		Map<FindingKey, CapabilityFinding> currentFindings = findingsByKey(current);
		List<RepositoryAnalysisDeltaResponse.FindingChange> changes = new ArrayList<>();
		for (FindingKey key : orderedKeys(baseFindings, currentFindings)) {
			CapabilityFinding baseFinding = baseFindings.get(key);
			CapabilityFinding currentFinding = currentFindings.get(key);
			FindingChangeKind kind = changeKind(baseFinding, currentFinding);
			CapabilityFinding finding = currentFinding == null ? baseFinding : currentFinding;
			changes.add(new RepositoryAnalysisDeltaResponse.FindingChange(
					key.ruleId(),
					key.dimension(),
					key.capability(),
					key.type().name(),
					finding.evidence(),
					finding.location(),
					kind,
					findingDirection(kind, key.type())));
		}
		return changes.stream()
				.sorted(Comparator
						.comparing((RepositoryAnalysisDeltaResponse.FindingChange change) -> change.direction() == DeltaDirection.UNCHANGED)
						.thenComparing(RepositoryAnalysisDeltaResponse.FindingChange::dimension)
						.thenComparing(RepositoryAnalysisDeltaResponse.FindingChange::capability)
						.thenComparing(RepositoryAnalysisDeltaResponse.FindingChange::ruleId))
				.toList();
	}

	private Map<String, DomainScore> analysisByDimension(AnalysisResponse analysis) {
		return analysis.dimensions()
				.stream()
				.collect(Collectors.toMap(DomainScore::dimension, Function.identity(), (left, right) -> right, LinkedHashMap::new));
	}

	private Map<CapabilityKey, CapabilityScore> capabilitiesByKey(AnalysisResponse analysis) {
		Map<CapabilityKey, CapabilityScore> scores = new LinkedHashMap<>();
		for (DomainScore dimension : analysis.dimensions()) {
			for (CapabilityScore capability : dimension.capabilityScores()) {
				scores.put(new CapabilityKey(dimension.dimension(), capability.capability()), capability);
			}
		}
		return scores;
	}

	private Map<FindingKey, CapabilityFinding> findingsByKey(AnalysisResponse analysis) {
		Map<FindingKey, CapabilityFinding> findings = new LinkedHashMap<>();
		for (DomainScore dimension : analysis.dimensions()) {
			for (CapabilityScore capability : dimension.capabilityScores()) {
				for (CapabilityFinding finding : capability.findings()) {
					findings.put(new FindingKey(
							finding.ruleId(),
							finding.dimension(),
							finding.capability(),
							finding.type()), finding);
				}
			}
		}
		return findings;
	}

	private <T> List<T> orderedKeys(Map<T, ?> base, Map<T, ?> current) {
		List<T> keys = new ArrayList<>(base.keySet());
		for (T key : current.keySet()) {
			if (!base.containsKey(key)) {
				keys.add(key);
			}
		}
		return keys;
	}

	private FindingChangeKind changeKind(CapabilityFinding base, CapabilityFinding current) {
		if (base == null) {
			return FindingChangeKind.ADDED;
		}
		if (current == null) {
			return FindingChangeKind.REMOVED;
		}
		return FindingChangeKind.UNCHANGED;
	}

	private DeltaDirection findingDirection(FindingChangeKind kind, FindingType type) {
		if (kind == FindingChangeKind.UNCHANGED) {
			return DeltaDirection.UNCHANGED;
		}
		boolean positive = type == FindingType.POSITIVE;
		if ((kind == FindingChangeKind.ADDED && positive) || (kind == FindingChangeKind.REMOVED && !positive)) {
			return DeltaDirection.IMPROVED;
		}
		return DeltaDirection.WORSENED;
	}

	private DeltaDirection direction(double scoreDelta, int levelDelta, AnalysisStatus baseStatus, AnalysisStatus currentStatus) {
		if (scoreDelta > 0 || (scoreDelta == 0 && levelDelta > 0)) {
			return DeltaDirection.IMPROVED;
		}
		if (scoreDelta < 0 || (scoreDelta == 0 && levelDelta < 0)) {
			return DeltaDirection.WORSENED;
		}
		if (baseStatus != null && currentStatus != null) {
			int statusDelta = statusRank(currentStatus) - statusRank(baseStatus);
			if (statusDelta > 0) {
				return DeltaDirection.IMPROVED;
			}
			if (statusDelta < 0) {
				return DeltaDirection.WORSENED;
			}
		}
		return DeltaDirection.UNCHANGED;
	}

	private int statusRank(AnalysisStatus status) {
		return switch (status) {
			case NOT_EVALUATED -> -1;
			case MISSING -> 0;
			case PARTIAL -> 1;
			case COMPLETE -> 2;
		};
	}

	private record CapabilityKey(String dimension, String capability) {
	}

	private record FindingKey(String ruleId, String dimension, String capability, FindingType type) {
	}
}
