package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CapabilityFindingSerializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializesHistoricalFindingsWithoutSourceSpan() throws Exception {
		CapabilityFinding finding = objectMapper.readValue("""
				{
				  "ruleId": "MISSING_TIMEOUT",
				  "dimension": "workflow_quality",
				  "capability": "Execution safety",
				  "type": "SMELL",
				  "evidence": "at least one job has no timeout-minutes",
				  "location": "jobs.build"
				}
				""", CapabilityFinding.class);

		assertThat(finding.source()).isNull();
		assertThat(finding.location()).isEqualTo("jobs.build");
	}
}
