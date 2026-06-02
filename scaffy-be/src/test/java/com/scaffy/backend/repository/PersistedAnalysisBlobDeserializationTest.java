package com.scaffy.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

class PersistedAnalysisBlobDeserializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializesNewFormatWithWrapper() throws Exception {
		String json = """
				{
				  "analysis": {
				    "provider": "github-actions",
				    "overallScore": 0.75,
				    "overallLevel": 3,
				    "overallStatus": "complete",
				    "dimensions": []
				  },
				  "workflowMetrics": {
				    "status": "AVAILABLE",
				    "metrics": {
				      "totalRuns": 4,
				      "successCount": 3,
				      "failureCount": 1,
				      "successRate": 0.75,
				      "failureRate": 0.25,
				      "recentFailures7d": 1,
				      "medianDurationSec": 120,
				      "p95DurationSec": 180,
				      "deployStability": 1.0,
				      "durationTrend": "stable",
				      "lastRunAt": null,
				      "lastSuccessAt": null,
				      "windowDays": 30,
				      "source": "github-actions",
				      "recentRuns": [],
				      "triggerDistribution": {},
				      "branchBreakdown": {}
				    },
				    "message": null
				  }
				}
				""";

		PersistedAnalysisBlob blob = objectMapper.readValue(json, PersistedAnalysisBlob.class);

		assertThat(blob.analysis()).isNotNull();
		assertThat(blob.analysis().overallLevel()).isEqualTo(3);
		assertThat(blob.workflowMetrics()).isNotNull();
		assertThat(blob.workflowMetrics().status()).isEqualTo(com.scaffy.backend.repository.metrics.MetricsStatus.AVAILABLE);
		assertThat(blob.workflowMetrics().metrics()).isNotNull();
		assertThat(blob.workflowMetrics().metrics().totalRuns()).isEqualTo(4);
	}

	@Test
	void deserializesLegacyFormatWithoutWrapper() throws Exception {
		String json = """
				{
				  "provider": "github-actions",
				  "overallScore": 0.4,
				  "overallLevel": 2,
				  "overallStatus": "partial",
				  "dimensions": []
				}
				""";

		JsonNode node = objectMapper.readTree(json);
		PersistedAnalysisBlob blob;
		if (node.has("analysis")) {
			blob = objectMapper.readValue(json, PersistedAnalysisBlob.class);
		}
		else {
			AnalysisResponse legacy = objectMapper.readValue(json, AnalysisResponse.class);
			blob = PersistedAnalysisBlob.legacy(legacy);
		}

		assertThat(blob.analysis()).isNotNull();
		assertThat(blob.analysis().overallLevel()).isEqualTo(2);
		assertThat(blob.workflowMetrics()).isNull();
	}
}