package com.scaffy.backend.repository.metrics;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecentRunSummary(
        long id,
        String displayName,
        String workflowName,
        String event,
        String branch,
        String conclusion,
        long durationSec,
        Instant startedAt) {
}