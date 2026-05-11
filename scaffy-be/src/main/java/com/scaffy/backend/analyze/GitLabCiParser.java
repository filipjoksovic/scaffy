package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class GitLabCiParser implements PipelineProviderParser {

	private static final Set<String> RESERVED_TOP_LEVEL_KEYS = Set.of(
			"stages",
			"variables",
			"workflow",
			"include",
			"default",
			"image",
			"services",
			"cache",
			"before_script",
			"after_script",
			"pages");

	@Override
	public PipelineProvider provider() {
		return PipelineProvider.GITLAB_CI;
	}

	@Override
	public PipelineDocument parse(Map<?, ?> root) {
		return new PipelineDocument(provider(), triggers(root), jobs(root));
	}

	private List<PipelineTrigger> triggers(Map<?, ?> root) {
		List<PipelineTrigger> triggers = new ArrayList<>();
		collectMergeRequestTriggers(YamlSupport.mapValue(root, "workflow").orElse(Map.of()), "workflow", triggers);
		for (Map.Entry<?, ?> entry : root.entrySet()) {
			String key = YamlSupport.asString(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> jobMap) || reserved(key)) {
				continue;
			}
			collectMergeRequestTriggers(jobMap, "jobs." + key, triggers);
		}
		return triggers;
	}

	private void collectMergeRequestTriggers(Map<?, ?> candidate, String baseLocation, List<PipelineTrigger> triggers) {
		Object rules = YamlSupport.value(candidate, "rules").orElse(null);
		if (!(rules instanceof List<?> ruleList)) {
			return;
		}
		for (int i = 0; i < ruleList.size(); i++) {
			Object ruleObject = ruleList.get(i);
			if (!(ruleObject instanceof Map<?, ?> ruleMap)) {
				continue;
			}
			String condition = YamlSupport.stringValue(ruleMap, "if").orElse("");
			if (condition.contains("merge_request_event")) {
				triggers.add(new PipelineTrigger("merge_request_event", true, baseLocation + ".rules[" + i + "].if"));
			}
		}
	}

	private List<PipelineJob> jobs(Map<?, ?> root) {
		List<PipelineJob> jobs = new ArrayList<>();
		for (Map.Entry<?, ?> entry : root.entrySet()) {
			String id = YamlSupport.asString(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> jobMap) || reserved(id) || id.startsWith(".")) {
				continue;
			}
			Object script = YamlSupport.value(jobMap, "script").orElse(null);
			if (script == null) {
				continue;
			}

			String stage = YamlSupport.stringValue(jobMap, "stage").orElse("test");
			boolean manualOnly = manualOnly(jobMap);
			List<PipelineStep> steps = scriptSteps(id, script);
			List<PipelineOutput> outputs = outputs(id, jobMap);
			jobs.add(new PipelineJob(id, id, stage, manualOnly, "jobs." + id, steps, outputs));
		}
		return jobs;
	}

	private List<PipelineStep> scriptSteps(String jobId, Object script) {
		List<PipelineStep> steps = new ArrayList<>();
		if (script instanceof List<?> scriptLines) {
			for (int i = 0; i < scriptLines.size(); i++) {
				String command = YamlSupport.asString(scriptLines.get(i));
				if (command != null) {
					steps.add(new PipelineStep(command, null, "jobs." + jobId + ".script[" + i + "]", i));
				}
			}
			return steps;
		}

		String command = YamlSupport.asString(script);
		if (command != null) {
			steps.add(new PipelineStep(command, null, "jobs." + jobId + ".script", 0));
		}
		return steps;
	}

	private List<PipelineOutput> outputs(String jobId, Map<?, ?> jobMap) {
		if (!YamlSupport.hasKey(jobMap, "artifacts")) {
			return List.of();
		}
		return List.of(new PipelineOutput("artifact", "artifacts", "jobs." + jobId + ".artifacts"));
	}

	private boolean manualOnly(Map<?, ?> jobMap) {
		String when = YamlSupport.stringValue(jobMap, "when").orElse("");
		if ("manual".equals(when)) {
			return true;
		}

		Object rules = YamlSupport.value(jobMap, "rules").orElse(null);
		if (!(rules instanceof List<?> ruleList) || ruleList.isEmpty()) {
			return false;
		}

		boolean sawRule = false;
		for (Object ruleObject : ruleList) {
			if (!(ruleObject instanceof Map<?, ?> ruleMap)) {
				continue;
			}
			sawRule = true;
			String ruleWhen = YamlSupport.stringValue(ruleMap, "when").orElse("");
			if (!"manual".equals(ruleWhen)) {
				return false;
			}
		}
		return sawRule;
	}

	private boolean reserved(String key) {
		return key == null || RESERVED_TOP_LEVEL_KEYS.contains(key);
	}
}
