package com.scaffy.backend.repository.metrics.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Low-level HTTP client for the GitHub Actions REST API.
 * Handles pagination and error classification; never throws on expected API failures.
 * All aggregation logic belongs in {@link GitHubMetricsProvider}.
 */
@Component
public class GitHubActionsApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubActionsApiClient.class);
    private static final String API_BASE = "https://api.github.com";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    // --- DTOs ---

    /** A single GitHub Actions workflow run. */
    public record WorkflowRun(
            long id,
            String name,
            String status,
            String conclusion,
            String event,
            String headBranch,
            Instant runStartedAt,
            Instant updatedAt,
            long workflowId,
            String workflowName) {
    }

    /** Aggregated result of one or more paginated API responses. */
    public record WorkflowRunsResponse(
            List<WorkflowRun> runs,
            int totalCount,
            boolean hasMorePages) {
    }

    /**
     * Sealed result type for a GitHub API call.
     * Pattern-match on subtypes to handle each outcome without catching exceptions.
     */
    public sealed interface ApiCallOutcome
            permits ApiCallOutcome.Success, ApiCallOutcome.RateLimited,
                    ApiCallOutcome.Unauthorized, ApiCallOutcome.Forbidden,
                    ApiCallOutcome.NotFound, ApiCallOutcome.ServerError,
                    ApiCallOutcome.NetworkError {

        /** API call succeeded; {@code data} contains aggregated runs. */
        record Success(WorkflowRunsResponse data) implements ApiCallOutcome {
        }

        /**
         * Rate limit exhausted (HTTP 403 with X-RateLimit-Remaining: 0, or HTTP 429).
         * {@code resetAt} is {@link Instant#EPOCH} when the reset time is unavailable.
         */
        record RateLimited(Instant resetAt) implements ApiCallOutcome {
        }

        /** HTTP 401 — token is invalid or expired. */
        record Unauthorized() implements ApiCallOutcome {
        }

        /** HTTP 403 without rate-limit header — likely a missing scope or permission. */
        record Forbidden(String reason) implements ApiCallOutcome {
        }

        /** HTTP 404 — workflow file not found in this repository. */
        record NotFound() implements ApiCallOutcome {
        }

        /** HTTP 5xx or other unexpected non-2xx status. */
        record ServerError(int status, String body) implements ApiCallOutcome {
        }

        /** Network-level failure: DNS error, timeout, connection refused, or thread interrupt. */
        record NetworkError(Throwable cause) implements ApiCallOutcome {
        }
    }

    // --- Client ---

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GitHubActionsApiClient(ObjectMapper objectMapper) {
        this(HttpClient.newHttpClient(), objectMapper);
    }

    GitHubActionsApiClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists workflow runs for a given workflow, following pagination up to {@code maxPages}.
     *
     * @param workflowFile filename or full path (e.g. {@code "ci.yml"} or
     *                     {@code ".github/workflows/ci.yml"}); paths are reduced to their
     *                     filename component before use.
     * @param perPage      runs per page (GitHub cap: 100)
     * @param maxPages     safety cap to prevent runaway pagination (e.g. 5 = up to 500 runs)
     * @return an {@link ApiCallOutcome} — never null, never throws
     */
    public ApiCallOutcome listWorkflowRuns(
            String owner,
            String repo,
            String workflowFile,
            String bearerToken,
            int perPage,
            int maxPages) {

        String filename = workflowFilename(workflowFile);
        List<WorkflowRun> allRuns = new ArrayList<>();
        int page = 1;
        int totalCount = 0;

        do {
            String path = "/repos/"
                    + URLEncoder.encode(owner, StandardCharsets.UTF_8)
                    + "/"
                    + URLEncoder.encode(repo, StandardCharsets.UTF_8)
                    + "/actions/workflows/"
                    + URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    + "/runs?per_page=" + perPage + "&page=" + page;

            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + path))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new ApiCallOutcome.NetworkError(ex);
            }
            catch (IOException ex) {
                log.warn("GitHub Actions API network error owner={} repo={} page={}", owner, repo, page);
                return new ApiCallOutcome.NetworkError(ex);
            }

            int status = response.statusCode();

            if (status == 401) {
                return new ApiCallOutcome.Unauthorized();
            }
            if (status == 403) {
                String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("1");
                if ("0".equals(remaining)) {
                    return new ApiCallOutcome.RateLimited(parseRateLimitReset(response));
                }
                return new ApiCallOutcome.Forbidden(truncate(response.body(), 200));
            }
            if (status == 429) {
                return new ApiCallOutcome.RateLimited(parseRateLimitReset(response));
            }
            if (status == 404) {
                return new ApiCallOutcome.NotFound();
            }
            if (status < 200 || status >= 300) {
                log.warn("GitHub Actions API unexpected status={} owner={} repo={}", status, owner, repo);
                return new ApiCallOutcome.ServerError(status, truncate(response.body(), 500));
            }

            Map<String, Object> body;
            try {
                body = objectMapper.readValue(response.body(), MAP_TYPE);
            }
            catch (JacksonException ex) {
                log.warn("GitHub Actions API response parse failure owner={} repo={} page={}", owner, repo, page);
                return new ApiCallOutcome.ServerError(status, "Response could not be parsed.");
            }

            totalCount = intValue(body.get("total_count"), 0);

            Object rawRuns = body.get("workflow_runs");
            if (rawRuns instanceof List<?> runList) {
                for (Object item : runList) {
                    if (item instanceof Map<?, ?> rawMap) {
                        @SuppressWarnings("unchecked")
                        WorkflowRun run = mapRun((Map<String, Object>) rawMap);
                        allRuns.add(run);
                    }
                }
            }

            page++;
        }
        while (page <= maxPages && allRuns.size() < totalCount);

        boolean hasMorePages = allRuns.size() < totalCount;
        return new ApiCallOutcome.Success(new WorkflowRunsResponse(List.copyOf(allRuns), totalCount, hasMorePages));
    }

    private WorkflowRun mapRun(Map<String, Object> map) {
        // GitHub API: "name" = workflow name (from YAML name: field)
        // "display_title" = run-specific title (commit message etc.); fall back to "name"
        String workflowName = stringValue(map.get("name"), null);
        String runName = stringValue(map.get("display_title"), workflowName);
        return new WorkflowRun(
                longValue(map.get("id")),
                runName,
                stringValue(map.get("status"), null),
                stringValue(map.get("conclusion"), null),
                stringValue(map.get("event"), null),
                stringValue(map.get("head_branch"), null),
                parseInstant(map.get("run_started_at")),
                parseInstant(map.get("updated_at")),
                longValue(map.get("workflow_id")),
                workflowName);
    }

    private static String workflowFilename(String workflowFile) {
        if (workflowFile == null || workflowFile.isBlank()) {
            return workflowFile;
        }
        int lastSlash = workflowFile.lastIndexOf('/');
        return lastSlash >= 0 ? workflowFile.substring(lastSlash + 1) : workflowFile;
    }

    private static Instant parseRateLimitReset(HttpResponse<String> response) {
        return response.headers()
                .firstValue("X-RateLimit-Reset")
                .map(s -> {
                    try {
                        return Instant.ofEpochSecond(Long.parseLong(s));
                    }
                    catch (NumberFormatException ex) {
                        return Instant.EPOCH;
                    }
                })
                .orElse(Instant.EPOCH);
    }

    private static Instant parseInstant(Object value) {
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Instant.parse(str);
            }
            catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private static long longValue(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
