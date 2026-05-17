package com.scaffy.backend.analyze;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PipelineAnalyzer {

	private static final Map<String, DimensionProfile> DIMENSIONS = Map.of(
			"build", new DimensionProfile(10, 0.20),
			"test", new DimensionProfile(20, 0.25),
			"code_analysis", new DimensionProfile(25, 0.10),
			"security_scanning", new DimensionProfile(27, 0.20),
			"artifacts", new DimensionProfile(28, 0.07),
			"deployment", new DimensionProfile(30, 0.15),
			"notifications", new DimensionProfile(40, 0.03));
	private static final DimensionProfile DEFAULT_DIMENSION = new DimensionProfile(100, 0.10);

	private final YamlPipelineParser yamlPipelineParser;
	private final ProviderDetector providerDetector;
	private final List<PipelineProviderParser> providerParsers;
	private final List<CapabilityRuleSet> ruleSets;

	public PipelineAnalyzer(
			YamlPipelineParser yamlPipelineParser,
			ProviderDetector providerDetector,
			List<PipelineProviderParser> providerParsers,
			List<CapabilityRuleSet> ruleSets) {
		this.yamlPipelineParser = yamlPipelineParser;
		this.providerDetector = providerDetector;
		this.providerParsers = providerParsers;
		this.ruleSets = ruleSets;
	}

	public AnalysisResponse analyze(String filename, String content) {
		Map<Object, Object> root = yamlPipelineParser.parse(content);
		PipelineProvider provider = providerDetector.detect(filename, content, root);
		PipelineProviderParser parser = parserFor(provider);
		PipelineDocument document = parser.parse(root);
		List<DimensionAnalysis> dimensions = ruleSets.stream()
				.sorted(Comparator.comparingInt(ruleSet -> dimensionOrder(ruleSet.dimension())))
				.map(ruleSet -> ruleSet.analyze(document))
				.toList();
		double overallScore = overallScore(dimensions);
		return new AnalysisResponse(
				provider,
				overallScore,
				AnalysisSupport.level(overallScore),
				AnalysisSupport.status(overallScore),
				confidence(dimensions),
				dimensions);
	}

	private PipelineProviderParser parserFor(PipelineProvider provider) {
		return providerParsers.stream()
				.filter(parser -> parser.provider() == provider)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No parser registered for provider " + provider));
	}

	private int dimensionOrder(String dimension) {
		return profile(dimension).order();
	}

	private double overallScore(List<DimensionAnalysis> dimensions) {
		if (dimensions.isEmpty()) {
			return 0.0;
		}
		double weightedTotal = dimensions.stream()
				.mapToDouble(dimension -> dimension.score() * dimensionWeight(dimension.dimension()))
				.sum();
		double weightTotal = dimensions.stream()
				.mapToDouble(dimension -> dimensionWeight(dimension.dimension()))
				.sum();
		return AnalysisSupport.round(weightedTotal / weightTotal);
	}

	private double dimensionWeight(String dimension) {
		return profile(dimension).weight();
	}

	private DimensionProfile profile(String dimension) {
		return DIMENSIONS.getOrDefault(dimension, DEFAULT_DIMENSION);
	}

	private Confidence confidence(List<DimensionAnalysis> dimensions) {
		if (dimensions.isEmpty()) {
			return Confidence.HIGH;
		}
		long highConfidenceDimensions = dimensions.stream()
				.filter(dimension -> dimension.confidence() == Confidence.HIGH)
				.count();
		if (highConfidenceDimensions > dimensions.size() / 2) {
			return Confidence.HIGH;
		}
		return Confidence.MEDIUM;
	}

	private record DimensionProfile(int order, double weight) {
	}
}
