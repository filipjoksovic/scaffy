package com.scaffy.backend.repository;

public record WorkflowCommitResult(
		String commitSha,
		String commitUrl,
		String branch) {
}
