package com.scaffy.backend.recommend;

import java.util.UUID;

public record FindingFixRequest(
		UUID analysisRunId,
		String provider,
		String workflowPath,
		String workflowContent,
		Finding finding) {

	public record Finding(
			String ruleId,
			String ruleLabel,
			String ruleDescription,
			String dimension,
			String capability,
			String type,
			String evidence,
			String location,
			Integer startLine,
			Integer endLine) {
	}
}
