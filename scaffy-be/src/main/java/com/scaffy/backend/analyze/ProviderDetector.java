package com.scaffy.backend.analyze;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ProviderDetector {

	private static final Pattern TOP_LEVEL_ON = Pattern.compile("(?m)^\\s*(?:on|'on'|\"on\")\\s*:");

	public PipelineProvider detect(String filename, String content, Map<?, ?> root) {
		boolean githubActions = githubFilename(filename) || githubStructure(content, root);
		boolean gitlabCi = gitlabFilename(filename) || gitlabStructure(root);

		if (githubActions == gitlabCi) {
			throw new PipelineAnalysisException(
					"Unsupported pipeline provider",
					"Only unambiguous GitHub Actions and GitLab CI YAML files are supported.");
		}

		return githubActions ? PipelineProvider.GITHUB_ACTIONS : PipelineProvider.GITLAB_CI;
	}

	private boolean githubFilename(String filename) {
		if (filename == null) {
			return false;
		}
		String normalized = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.contains(".github/workflows/")
				&& (normalized.endsWith(".yml") || normalized.endsWith(".yaml"));
	}

	private boolean gitlabFilename(String filename) {
		if (filename == null) {
			return false;
		}
		String normalized = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.endsWith(".gitlab-ci.yml") || normalized.endsWith(".gitlab-ci.yaml");
	}

	private boolean githubStructure(String content, Map<?, ?> root) {
		return YamlSupport.hasKey(root, "jobs")
				&& (TOP_LEVEL_ON.matcher(content).find() || YamlSupport.hasKey(root, "on"));
	}

	private boolean gitlabStructure(Map<?, ?> root) {
		if (YamlSupport.hasKey(root, "stages")) {
			return true;
		}
		for (Map.Entry<?, ?> entry : root.entrySet()) {
			String key = YamlSupport.asString(entry.getKey());
			if (key == null || key.startsWith(".")) {
				continue;
			}
			if (entry.getValue() instanceof Map<?, ?> candidate && YamlSupport.hasKey(candidate, "script")) {
				return true;
			}
		}
		return false;
	}
}
