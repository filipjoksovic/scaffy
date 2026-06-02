package com.scaffy.backend.repository.metrics;

/**
 * Describes the outcome of a workflow metrics fetch attempt.
 */
public enum MetricsStatus {

    /** Metrics were successfully fetched and aggregated. */
    AVAILABLE,

    /** The user has no OAuth connection for this provider in this workspace. */
    TOKEN_MISSING,

    /** The OAuth token has expired; the user must reconnect. */
    TOKEN_EXPIRED,

    /** The OAuth token lacks the required scopes (e.g., {@code actions:read}). */
    SCOPE_INSUFFICIENT,

    /** The provider API rate limit was exceeded. */
    RATE_LIMITED,

    /** The workflow file could not be mapped to a provider workflow ID. */
    WORKFLOW_NOT_FOUND,

    /** The workflow exists but has no runs within the observation window. */
    NO_RUNS_IN_WINDOW,

    /** The provider API returned a 5xx or otherwise unexpected response. */
    PROVIDER_ERROR,

    /** This provider does not yet support runtime metrics. */
    UNSUPPORTED

}
