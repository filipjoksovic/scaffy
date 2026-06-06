package com.scaffy.backend.repository.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FailureReasonInsight(
        String reason,
        int count,
        double share) {
}
