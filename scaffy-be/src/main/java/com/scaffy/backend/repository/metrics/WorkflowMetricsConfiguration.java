package com.scaffy.backend.repository.metrics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.scaffy.backend.repository.metrics.cache.CachingWorkflowMetricsProvider;
import com.scaffy.backend.repository.metrics.cache.WorkflowMetricsCache;

/**
 * Wires the metrics provider layer: wraps each raw {@link WorkflowMetricsProvider} @Component
 * in a {@link CachingWorkflowMetricsProvider} and exposes the result as a
 * {@code Map<String, WorkflowMetricsProvider>} keyed by provider name.
 *
 * <p>Consumers (e.g., {@code RepositoryAnalysisService}) inject the map and look up providers
 * by name, e.g. {@code providers.get("github-actions")}.
 *
 * <p>Spring wiring notes:
 * <ul>
 *   <li>{@code rawProviders} receives all {@code @Component}-registered {@link WorkflowMetricsProvider}
 *       beans (currently: {@code GitHubMetricsProvider}).
 *   <li>The returned {@code Map} bean is of type {@code Map<String, WorkflowMetricsProvider>},
 *       which is a different type from {@code WorkflowMetricsProvider}, so it does NOT feed
 *       back into {@code rawProviders} and cannot cause circular dependency or duplicates.
 *   <li>{@link CachingWorkflowMetricsProvider} is not a {@code @Component}, so it is never
 *       included in {@code rawProviders}. The defensive {@code instanceof} filter is a guard
 *       against future misconfigurations only.
 * </ul>
 */
@Configuration
public class WorkflowMetricsConfiguration {

    @Bean
    public Map<String, WorkflowMetricsProvider> cachedMetricsProvidersByName(
            List<WorkflowMetricsProvider> rawProviders,
            WorkflowMetricsCache cache) {
        return rawProviders.stream()
                .filter(p -> !(p instanceof CachingWorkflowMetricsProvider))
                .collect(Collectors.toUnmodifiableMap(
                        WorkflowMetricsProvider::provider,
                        p -> new CachingWorkflowMetricsProvider(p, cache)
                ));
    }
}
