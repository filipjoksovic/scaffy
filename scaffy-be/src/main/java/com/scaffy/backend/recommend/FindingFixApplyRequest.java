package com.scaffy.backend.recommend;

import java.util.UUID;

public record FindingFixApplyRequest(
		UUID analysisRunId,
		FindingFixRequest.Finding finding,
		String workflowPath,
		String workflowContent,
		String commitMessage) {
}
