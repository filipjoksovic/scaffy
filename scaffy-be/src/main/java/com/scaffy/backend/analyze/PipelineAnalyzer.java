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
		return new AnalysisResponse(provider, dimensions);
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
}
