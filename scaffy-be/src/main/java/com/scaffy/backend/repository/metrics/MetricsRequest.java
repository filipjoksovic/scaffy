package com.scaffy.backend.repository.metrics;

import java.util.UUID;

/**
 * Describes the parameters for a single workflow metrics fetch.
 * Internal type — not serialized to JSON.
 */
public record MetricsRequest(
        UUID workspaceId,
        UUID userId,
        String provider,
        String providerInstance,
        String owner,
        String repo,
        String workflowFile,
        int windowDays) {
}
