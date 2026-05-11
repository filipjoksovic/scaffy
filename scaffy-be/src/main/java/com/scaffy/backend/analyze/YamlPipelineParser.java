package com.scaffy.backend.analyze;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Component
public class YamlPipelineParser {

	public Map<?, ?> parse(String content) {
		try {
			LoaderOptions loaderOptions = new LoaderOptions();
			loaderOptions.setCodePointLimit(1_000_000);
			Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
			Object loaded = yaml.load(content);
			if (loaded == null) {
				throw PipelineAnalysisException.invalidUpload("Uploaded pipeline file must not be empty.");
			}
			if (!(loaded instanceof Map<?, ?> root)) {
				throw unsupportedProvider();
			}
			return root;
		}
		catch (PipelineAnalysisException ex) {
			throw ex;
		}
		catch (YAMLException ex) {
			throw new PipelineAnalysisException("Invalid pipeline YAML", "Uploaded pipeline YAML could not be parsed.");
		}
	}

	private PipelineAnalysisException unsupportedProvider() {
		return new PipelineAnalysisException(
				"Unsupported pipeline provider",
				"Only unambiguous GitHub Actions and GitLab CI YAML files are supported.");
	}
}
