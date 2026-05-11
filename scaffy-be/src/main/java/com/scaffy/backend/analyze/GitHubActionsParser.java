package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class GitHubActionsParser implements PipelineProviderParser {

	@Override
	public PipelineProvider provider() {
		return PipelineProvider.GITHUB_ACTIONS;
	}

	@Override
	public PipelineDocument parse(Map<Object, Object> root) {
		List<PipelineTrigger> triggers = triggers(root);
		List<PipelineJob> jobs = jobs(root);
		return new PipelineDocument(provider(), triggers, jobs);
	}

	private List<PipelineTrigger> triggers(Map<Object, Object> root) {
		Object on = YamlSupport.value(root, "on").orElse(null);
		if (on == null) {
			return List.of();
		}

		List<PipelineTrigger> triggers = new ArrayList<>();
		YamlSupport.asMap(on).ifPresent(triggerMap -> addMapTriggers(triggers, triggerMap));
		if (!triggers.isEmpty()) {
			return triggers;
		}

		YamlSupport.asList(on).ifPresent(triggerList -> addListTriggers(triggers, triggerList));
		if (!triggers.isEmpty()) {
			return triggers;
		}

		addTrigger(triggers, YamlSupport.asString(on), "on");
		return triggers;
	}

	private void addMapTriggers(List<PipelineTrigger> triggers, Map<Object, Object> triggerMap) {
		for (Object key : triggerMap.keySet()) {
			addTrigger(triggers, YamlSupport.asString(key), "on." + YamlSupport.asString(key));
		}
	}

	private void addListTriggers(List<PipelineTrigger> triggers, List<Object> triggerList) {
		for (int i = 0; i < triggerList.size(); i++) {
			addTrigger(triggers, YamlSupport.asString(triggerList.get(i)), "on[" + i + "]");
		}
	}

	private void addTrigger(List<PipelineTrigger> triggers, String name, String location) {
		if (name == null || name.isBlank()) {
			return;
		}
		boolean automatic = "push".equals(name) || "pull_request".equals(name);
		triggers.add(new PipelineTrigger(name, automatic, location));
	}

	private List<PipelineJob> jobs(Map<Object, Object> root) {
		Map<Object, Object> jobs = YamlSupport.mapValue(root, "jobs").orElse(Map.of());
		List<PipelineJob> parsedJobs = new ArrayList<>();
		for (Map.Entry<Object, Object> entry : jobs.entrySet()) {
			YamlSupport.asMap(entry.getValue())
					.ifPresent(jobMap -> parsedJobs.add(job(YamlSupport.asString(entry.getKey()), jobMap)));
		}
		return parsedJobs;
	}

	private PipelineJob job(String id, Map<Object, Object> jobMap) {
		String name = YamlSupport.stringValue(jobMap, "name").orElse(id);
		return new PipelineJob(id, name, null, false, "jobs." + id, steps(id, jobMap), List.of());
	}

	private List<PipelineStep> steps(String jobId, Map<Object, Object> jobMap) {
		return YamlSupport.value(jobMap, "steps")
				.flatMap(YamlSupport::asList)
				.map(stepList -> stepList(jobId, stepList))
				.orElse(List.of());
	}

	private List<PipelineStep> stepList(String jobId, List<Object> stepList) {
		List<PipelineStep> steps = new ArrayList<>();
		for (int i = 0; i < stepList.size(); i++) {
			addStep(steps, jobId, stepList.get(i), i);
		}
		return steps;
	}

	private void addStep(List<PipelineStep> steps, String jobId, Object stepObject, int index) {
		YamlSupport.asMap(stepObject).ifPresent(stepMap -> {
			String command = YamlSupport.stringValue(stepMap, "run").orElse(null);
			String uses = YamlSupport.stringValue(stepMap, "uses").orElse(null);
			if (command != null || uses != null) {
				steps.add(new PipelineStep(command, uses, "jobs." + jobId + ".steps[" + index + "]" + suffix(command, uses), index));
			}
		});
	}

	private String suffix(String command, String uses) {
		if (command != null) {
			return ".run";
		}
		if (uses != null) {
			return ".uses";
		}
		return "";
	}
}
