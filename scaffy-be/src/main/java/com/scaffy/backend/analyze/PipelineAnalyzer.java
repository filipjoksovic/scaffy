package com.scaffy.backend.analyze;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class PipelineAnalyzer {

	private final YamlPipelineParser yamlPipelineParser;
	private final ProviderDetector providerDetector;
	private final List<PipelineProviderParser> providerParsers;
	private final List<CapabilityRuleSet> ruleSets;
	private final ScoringEngine scoringEngine;

	public PipelineAnalyzer(
			YamlPipelineParser yamlPipelineParser,
			ProviderDetector providerDetector,
			List<PipelineProviderParser> providerParsers,
			List<CapabilityRuleSet> ruleSets,
			ScoringEngine scoringEngine) {
		this.yamlPipelineParser = yamlPipelineParser;
		this.providerDetector = providerDetector;
		this.providerParsers = providerParsers;
		this.ruleSets = ruleSets;
		this.scoringEngine = scoringEngine;
	}

	public AnalysisResponse analyze(String filename, String content) {
		Map<Object, Object> root = yamlPipelineParser.parse(content);
		YamlSourceIndex sourceIndex = yamlPipelineParser.sourceIndex(content);
		PipelineProvider provider = providerDetector.detect(filename, content, root);
		PipelineProviderParser parser = parserFor(provider);
		PipelineDocument document = parser.parse(root);

		List<CapabilityFinding> allFindings = ruleSets.stream()
				.flatMap(ruleSet -> ruleSet.detect(document).stream())
				.map(finding -> finding.withSource(sourceIndex.sourceFor(finding.location())))
				.toList();

		Map<String, List<CapabilityFinding>> byDimension = allFindings.stream()
				.collect(Collectors.groupingBy(CapabilityFinding::dimension, LinkedHashMap::new, Collectors.toList()));

		List<String> dimensionOrder = ruleSets.stream()
				.map(CapabilityRuleSet::dimension)
				.distinct()
				.toList();

		List<DomainScore> domainScores = dimensionOrder.stream()
				.map(dim -> scoringEngine.score(dim, byDimension.getOrDefault(dim, List.of())))
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

	private PipelineProviderParser parserFor(PipelineProvider provider) {
		return providerParsers.stream()
				.filter(parser -> parser.provider() == provider)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No parser registered for provider " + provider));
	}
}
