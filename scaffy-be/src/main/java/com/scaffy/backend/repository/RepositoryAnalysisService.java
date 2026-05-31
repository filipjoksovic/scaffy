package com.scaffy.backend.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.AnalysisStatus;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.CapabilityScore;
import com.scaffy.backend.analyze.DomainScore;
import com.scaffy.backend.analyze.FindingType;
import com.scaffy.backend.analyze.PipelineAnalyzer;

@Service
public class RepositoryAnalysisService {

	private static final String CONNECTION_NOT_FOUND = "Repository connection not found.";

	private final GitHubWorkflowClient gitHubWorkflowClient;
	private final GitLabWorkflowClient gitLabWorkflowClient;
	private final PipelineAnalyzer pipelineAnalyzer;
	private final RepositoryConnectionRepository repository;
	private final RepositoryAnalysisRepository analysisRepository;

	public RepositoryAnalysisService(
			GitHubWorkflowClient gitHubWorkflowClient,
			GitLabWorkflowClient gitLabWorkflowClient,
			PipelineAnalyzer pipelineAnalyzer,
			RepositoryConnectionRepository repository,
			RepositoryAnalysisRepository analysisRepository) {
		this.gitHubWorkflowClient = gitHubWorkflowClient;
		this.gitLabWorkflowClient = gitLabWorkflowClient;
		this.pipelineAnalyzer = pipelineAnalyzer;
		this.repository = repository;
		this.analysisRepository = analysisRepository;
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
		WorkflowSource workflow = fetchWorkflow(connection);
		AnalysisResponse analysis = pipelineAnalyzer.analyze(workflow.path(), workflow.content());
		PersistedRepositoryAnalysis persisted = analysisRepository.insert(
				connection.id(), workflow.path(), workflow.content(), analysis);
		return RepositoryAnalysisResponse.from(connection, persisted);
	}

	private WorkflowSource fetchWorkflow(RepositoryConnection connection) {
		return switch (connection.provider()) {
			case "github" -> {
				GitHubWorkflowFile file = gitHubWorkflowClient.findWorkflow(
						connection.workspaceId(), connection.userId(), connection);
				yield new WorkflowSource(file.path(), file.content());
			}
			case "gitlab" -> {
				GitLabCiFile file = gitLabWorkflowClient.findCiFile(
						connection.workspaceId(), connection.userId(), connection);
				yield new WorkflowSource(file.path(), file.content());
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
