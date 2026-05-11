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
	public PipelineDocument parse(Map<?, ?> root) {
		List<PipelineTrigger> triggers = triggers(root);
		List<PipelineJob> jobs = jobs(root);
		return new PipelineDocument(provider(), triggers, jobs);
	}

	private List<PipelineTrigger> triggers(Map<?, ?> root) {
		Object on = YamlSupport.value(root, "on").orElse(null);
		if (on == null) {
			return List.of();
		}

		List<PipelineTrigger> triggers = new ArrayList<>();
		if (on instanceof Map<?, ?> triggerMap) {
			for (Object key : triggerMap.keySet()) {
				addTrigger(triggers, YamlSupport.asString(key), "on." + YamlSupport.asString(key));
			}
			return triggers;
		}

		if (on instanceof List<?> triggerList) {
			for (int i = 0; i < triggerList.size(); i++) {
				addTrigger(triggers, YamlSupport.asString(triggerList.get(i)), "on[" + i + "]");
			}
			return triggers;
		}

		addTrigger(triggers, YamlSupport.asString(on), "on");
		return triggers;
	}

	private void addTrigger(List<PipelineTrigger> triggers, String name, String location) {
		if (name == null || name.isBlank()) {
			return;
		}
		boolean automatic = "push".equals(name) || "pull_request".equals(name);
		triggers.add(new PipelineTrigger(name, automatic, location));
	}

	private List<PipelineJob> jobs(Map<?, ?> root) {
		Map<?, ?> jobs = YamlSupport.mapValue(root, "jobs").orElse(Map.of());
		List<PipelineJob> parsedJobs = new ArrayList<>();
		for (Map.Entry<?, ?> entry : jobs.entrySet()) {
			String id = YamlSupport.asString(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> jobMap)) {
				continue;
			}

			String name = YamlSupport.stringValue(jobMap, "name").orElse(id);
			List<PipelineStep> steps = new ArrayList<>();
			Object stepsObject = YamlSupport.value(jobMap, "steps").orElse(null);
			if (stepsObject instanceof List<?> stepList) {
				for (int i = 0; i < stepList.size(); i++) {
					Object stepObject = stepList.get(i);
					if (!(stepObject instanceof Map<?, ?> stepMap)) {
						continue;
					}
					String command = YamlSupport.stringValue(stepMap, "run").orElse(null);
					String uses = YamlSupport.stringValue(stepMap, "uses").orElse(null);
					if (command != null || uses != null) {
						steps.add(new PipelineStep(command, uses, "jobs." + id + ".steps[" + i + "]" + suffix(command, uses), i));
					}
				}
			}

			parsedJobs.add(new PipelineJob(id, name, null, false, "jobs." + id, steps, List.of()));
		}
		return parsedJobs;
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
