package com.scaffy.backend.repository.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchHealth(
        int totalRuns,
        int failureCount,
        double failureRate) {
}