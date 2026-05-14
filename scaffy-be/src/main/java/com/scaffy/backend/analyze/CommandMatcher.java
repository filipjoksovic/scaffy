package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

final class CommandMatcher {

	private CommandMatcher() {
	}

	static List<CommandMatch> findMatches(PipelineDocument document, List<CommandRule> rules) {
		List<CommandMatch> matches = new ArrayList<>();
		for (PipelineJob job : document.jobs()) {
			for (PipelineStep step : job.steps()) {
				addStepMatches(matches, job, step, rules);
			}
		}
		return matches;
	}

	private static void addStepMatches(
			List<CommandMatch> matches,
			PipelineJob job,
			PipelineStep step,
			List<CommandRule> rules) {
		if (step.command() == null || step.command().isBlank()) {
			return;
		}
		for (CommandRule rule : rules) {
			Matcher matcher = rule.pattern().matcher(step.command());
			while (matcher.find()) {
				String evidence = matcher.group("cmd").trim();
				matches.add(new CommandMatch(job, step, evidence, step.location(), matcher.start("cmd"), rule));
			}
		}
	}
}
