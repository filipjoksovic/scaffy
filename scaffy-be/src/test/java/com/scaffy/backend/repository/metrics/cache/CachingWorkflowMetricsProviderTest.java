package com.scaffy.backend.repository.metrics.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.scaffy.backend.repository.metrics.MetricsRequest;
import com.scaffy.backend.repository.metrics.MetricsStatus;
import com.scaffy.backend.repository.metrics.WorkflowMetrics;
import com.scaffy.backend.repository.metrics.WorkflowMetricsProvider;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

class CachingWorkflowMetricsProviderTest {

	@Test
	void availableResultIsCachedAndReturnedOnSecondCall() {
		WorkflowMetricsProvider delegate = mock(WorkflowMetricsProvider.class);
		WorkflowMetricsCache cache = new WorkflowMetricsCache();
		CachingWorkflowMetricsProvider provider = new CachingWorkflowMetricsProvider(delegate, cache);
		MetricsRequest request = request();
		WorkflowMetricsResult available = WorkflowMetricsResult.available(WorkflowMetrics.empty(30, "github-actions"));

		when(delegate.fetchMetrics(request)).thenReturn(available);

		WorkflowMetricsResult first = provider.fetchMetrics(request);
		WorkflowMetricsResult second = provider.fetchMetrics(request);

		verify(delegate, times(1)).fetchMetrics(request);
		assertThat(second).isSameAs(first);
	}

	@Test
	void unavailableResultIsNotCached() {
		WorkflowMetricsProvider delegate = mock(WorkflowMetricsProvider.class);
		WorkflowMetricsCache cache = new WorkflowMetricsCache();
		CachingWorkflowMetricsProvider provider = new CachingWorkflowMetricsProvider(delegate, cache);
		MetricsRequest request = request();
		WorkflowMetricsResult unavailable = WorkflowMetricsResult.unavailable(MetricsStatus.TOKEN_MISSING, "missing");

		when(delegate.fetchMetrics(request)).thenReturn(unavailable);

		provider.fetchMetrics(request);
		provider.fetchMetrics(request);

		verify(delegate, times(2)).fetchMetrics(request);
	}

	private MetricsRequest request() {
		return new MetricsRequest(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"github-actions",
				"",
				"scaffy-labs",
				"demo-app",
				".github/workflows/ci.yml",
				30);
	}
}