package com.scaffy.backend.analyze;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PipelineAnalyzer {

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
				level(overallScore),
				status(overallScore),
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
		return switch (dimension) {
			case "build" -> 10;
			case "test" -> 20;
			case "code_analysis" -> 25;
			case "security_scanning" -> 27;
			case "artifacts" -> 28;
			case "deployment" -> 30;
			case "notifications" -> 40;
			default -> 100;
		};
	}

	private double overallScore(List<DimensionAnalysis> dimensions) {
		if (dimensions.isEmpty()) {
			return 0.0;
		}
		double total = dimensions.stream()
				.mapToDouble(DimensionAnalysis::score)
				.sum();
		return round(total / dimensions.size());
	}

	private AnalysisStatus status(double score) {
		if (score == 0.0) {
			return AnalysisStatus.MISSING;
		}
		if (score >= 0.8) {
			return AnalysisStatus.COMPLETE;
		}
		return AnalysisStatus.PARTIAL;
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

	private int level(double score) {
		if (score == 0.0) {
			return 1;
		}
		if (score < 0.4) {
			return 2;
		}
		if (score < 0.6) {
			return 3;
		}
		if (score < 0.8) {
			return 4;
		}
		return 5;
	}

	private double round(double score) {
		return Math.round(score * 100.0) / 100.0;
	}
}
