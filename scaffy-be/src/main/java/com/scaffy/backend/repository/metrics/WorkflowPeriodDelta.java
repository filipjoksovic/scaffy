package com.scaffy.backend.repository.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowPeriodDelta(
        double previousSuccessRate,
        double currentSuccessRate,
        double successRateDelta,
        int previousFailureCount,
        int currentFailureCount,
        int failureCountDelta,
        long previousMedianDurationSec,
        long currentMedianDurationSec,
        long medianDurationDeltaSec,
        long previousP95DurationSec,
        long currentP95DurationSec,
        long p95DurationDeltaSec,
        String trend) {
}
