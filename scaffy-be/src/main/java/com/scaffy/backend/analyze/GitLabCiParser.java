package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class GitLabCiParser implements PipelineProviderParser {

	private static final String WORKFLOW = "workflow";
	private static final String JOBS_LOCATION_PREFIX = "jobs.";

	private static final Set<String> RESERVED_TOP_LEVEL_KEYS = Set.of(
			"stages",
			"variables",
			WORKFLOW,
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
	public PipelineDocument parse(Map<Object, Object> root) {
		return new PipelineDocument(provider(), triggers(root), jobs(root));
	}

	private List<PipelineTrigger> triggers(Map<Object, Object> root) {
		List<PipelineTrigger> triggers = new ArrayList<>();
		collectMergeRequestTriggers(YamlSupport.mapValue(root, WORKFLOW).orElse(Map.of()), WORKFLOW, triggers);
		for (Map.Entry<Object, Object> entry : root.entrySet()) {
			String key = YamlSupport.asString(entry.getKey());
			YamlSupport.asMap(entry.getValue())
					.filter(jobMap -> !reserved(key))
					.ifPresent(jobMap -> collectMergeRequestTriggers(jobMap, JOBS_LOCATION_PREFIX + key, triggers));
		}
		return triggers;
	}

	private void collectMergeRequestTriggers(Map<Object, Object> candidate, String baseLocation, List<PipelineTrigger> triggers) {
		Object rules = YamlSupport.value(candidate, "rules").orElse(null);
		List<Object> ruleList = YamlSupport.asList(rules).orElse(List.of());
		for (int i = 0; i < ruleList.size(); i++) {
			addMergeRequestTrigger(ruleList.get(i), baseLocation, i, triggers);
		}
	}

	private void addMergeRequestTrigger(Object ruleObject, String baseLocation, int index, List<PipelineTrigger> triggers) {
		YamlSupport.asMap(ruleObject).ifPresent(ruleMap -> {
			String condition = YamlSupport.stringValue(ruleMap, "if").orElse("");
			if (condition.contains("merge_request_event")) {
				triggers.add(new PipelineTrigger("merge_request_event", true, baseLocation + ".rules[" + index + "].if"));
			}
		});
	}

	private List<PipelineJob> jobs(Map<Object, Object> root) {
		List<PipelineJob> jobs = new ArrayList<>();
		for (Map.Entry<Object, Object> entry : root.entrySet()) {
			String id = YamlSupport.asString(entry.getKey());
			if (jobCandidate(id, entry.getValue())) {
				Map<Object, Object> jobMap = YamlSupport.asMap(entry.getValue()).orElseThrow();
				Object script = YamlSupport.value(jobMap, "script").orElseThrow();
				jobs.add(job(id, jobMap, script));
			}
		}
		return jobs;
	}

	private boolean jobCandidate(String id, Object value) {
		return id != null
				&& !reserved(id)
				&& !id.startsWith(".")
				&& YamlSupport.asMap(value).filter(jobMap -> YamlSupport.hasKey(jobMap, "script")).isPresent();
	}

	private PipelineJob job(String id, Map<Object, Object> jobMap, Object script) {
		String stage = YamlSupport.stringValue(jobMap, "stage").orElse("test");
		String environment = environment(jobMap);
		boolean manualOnly = manualOnly(jobMap);
		List<PipelineStep> steps = scriptSteps(id, script);
		List<PipelineOutput> outputs = outputs(id, jobMap);
		return new PipelineJob(id, id, stage, environment, manualOnly, JOBS_LOCATION_PREFIX + id, steps, outputs);
	}

	private String environment(Map<Object, Object> jobMap) {
		Object environment = YamlSupport.value(jobMap, "environment").orElse(null);
		Optional<Map<Object, Object>> environmentMap = YamlSupport.asMap(environment);
		if (environmentMap.isPresent()) {
			return environmentMap
					.flatMap(map -> YamlSupport.stringValue(map, "name"))
					.orElse(null);
		}
		return YamlSupport.asString(environment);
	}

	private List<PipelineStep> scriptSteps(String jobId, Object script) {
		List<PipelineStep> steps = new ArrayList<>();
		Optional<List<Object>> scriptList = YamlSupport.asList(script);
		if (scriptList.isPresent()) {
			List<Object> scriptLines = scriptList.get();
			for (int i = 0; i < scriptLines.size(); i++) {
				String command = YamlSupport.asString(scriptLines.get(i));
				if (command != null) {
					steps.add(new PipelineStep(command, null, JOBS_LOCATION_PREFIX + jobId + ".script[" + i + "]", i));
				}
			}
			return steps;
		}

		String command = YamlSupport.asString(script);
		if (command != null) {
			steps.add(new PipelineStep(command, null, JOBS_LOCATION_PREFIX + jobId + ".script", 0));
		}
		return steps;
	}

	private List<PipelineOutput> outputs(String jobId, Map<Object, Object> jobMap) {
		if (!YamlSupport.hasKey(jobMap, "artifacts")) {
			return List.of();
		}
		return List.of(new PipelineOutput("artifact", "artifacts", JOBS_LOCATION_PREFIX + jobId + ".artifacts"));
	}

	private boolean manualOnly(Map<Object, Object> jobMap) {
		String when = YamlSupport.stringValue(jobMap, "when").orElse("");
		if ("manual".equals(when)) {
			return true;
		}

		Object rules = YamlSupport.value(jobMap, "rules").orElse(null);
		List<Object> ruleList = YamlSupport.asList(rules).orElse(List.of());
		if (ruleList.isEmpty()) {
			return false;
		}

		boolean sawRule = false;
		for (Object ruleObject : ruleList) {
			Optional<Map<Object, Object>> rule = YamlSupport.asMap(ruleObject);
			if (rule.isPresent()) {
				Map<Object, Object> ruleMap = rule.get();
				sawRule = true;
				String ruleWhen = YamlSupport.stringValue(ruleMap, "when").orElse("");
				if (!"manual".equals(ruleWhen)) {
					return false;
				}
			}
		}
		return sawRule;
	}

	private boolean reserved(String key) {
		return key == null || RESERVED_TOP_LEVEL_KEYS.contains(key);
	}
}
