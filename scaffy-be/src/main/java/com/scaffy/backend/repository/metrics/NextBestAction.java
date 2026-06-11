package com.scaffy.backend.repository.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NextBestAction(
        String title,
        String detail,
        String severity,
        String target) {
}
