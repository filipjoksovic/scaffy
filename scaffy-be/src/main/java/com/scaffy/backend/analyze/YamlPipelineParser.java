package com.scaffy.backend.analyze;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

@Component
public class YamlPipelineParser {

	public Map<Object, Object> parse(String content) {
		try {
			LoaderOptions loaderOptions = new LoaderOptions();
			loaderOptions.setCodePointLimit(1_000_000);
			Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
			Object loaded = yaml.load(content);
			if (loaded == null) {
				throw PipelineAnalysisException.invalidUpload("Uploaded pipeline file must not be empty.");
			}
			if (!(loaded instanceof Map)) {
				throw unsupportedProvider();
			}
			return rootMap(loaded);
		}
		catch (PipelineAnalysisException ex) {
			throw ex;
		}
		catch (YAMLException ex) {
			throw new PipelineAnalysisException("Invalid pipeline YAML", "Uploaded pipeline YAML could not be parsed.");
		}
	}

	public YamlSourceIndex sourceIndex(String content) {
		try {
			LoaderOptions loaderOptions = new LoaderOptions();
			loaderOptions.setCodePointLimit(1_000_000);
			Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
			Node root = yaml.compose(new StringReader(content));
			Map<String, SourceSpan> spans = new LinkedHashMap<>();
			collect("", root, spans);
			return new YamlSourceIndex(spans);
		}
		catch (YAMLException ex) {
			return new YamlSourceIndex(Map.of());
		}
	}

	private void collect(String path, Node node, Map<String, SourceSpan> spans) {
		if (node == null) {
			return;
		}
		if (!path.isBlank()) {
			spans.put(path, sourceSpan(path, node));
		}
		collectChildren(path, node, spans);
	}

	private void collectChildren(String path, Node node, Map<String, SourceSpan> spans) {
		if (node instanceof MappingNode mappingNode) {
			for (NodeTuple tuple : mappingNode.getValue()) {
				String key = scalarValue(tuple.getKeyNode());
				if (key == null || key.isBlank()) {
					continue;
				}
				String childPath = path.isBlank() ? key : path + "." + key;
				spans.put(childPath, sourceSpan(childPath, tuple.getKeyNode(), tuple.getValueNode()));
				collectChildren(childPath, tuple.getValueNode(), spans);
			}
		}
		else if (node instanceof SequenceNode sequenceNode) {
			for (int i = 0; i < sequenceNode.getValue().size(); i++) {
				collect(path + "[" + i + "]", sequenceNode.getValue().get(i), spans);
			}
		}
	}

	private String scalarValue(Node node) {
		if (node instanceof ScalarNode scalarNode) {
			return scalarNode.getValue();
		}
		return null;
	}

	private SourceSpan sourceSpan(String path, Node node) {
		return sourceSpan(path, node, node);
	}

	private SourceSpan sourceSpan(String path, Node startNode, Node endNode) {
		Mark start = startNode.getStartMark();
		Mark end = endNode.getEndMark();
		return new SourceSpan(
				path,
				oneBasedLine(start),
				oneBasedColumn(start),
				oneBasedLine(end),
				oneBasedColumn(end));
	}

	private int oneBasedLine(Mark mark) {
		return mark == null ? 1 : mark.getLine() + 1;
	}

	private int oneBasedColumn(Mark mark) {
		return mark == null ? 1 : mark.getColumn() + 1;
	}

	private PipelineAnalysisException unsupportedProvider() {
		return new PipelineAnalysisException(
				"Unsupported pipeline provider",
				"Only unambiguous GitHub Actions and GitLab CI YAML files are supported.");
	}

	@SuppressWarnings("unchecked")
	private Map<Object, Object> rootMap(Object loaded) {
		return (Map<Object, Object>) loaded;
	}
}
