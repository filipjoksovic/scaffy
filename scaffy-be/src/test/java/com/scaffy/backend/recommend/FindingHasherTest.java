package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FindingHasherTest {

	@Test
	void producesStableHexHashForIdenticalFindings() {
		FindingFixRequest.Finding a = finding("MISSING_TIMEOUT", "Execution safety", "jobs.build", "timeout-minutes not set");
		FindingFixRequest.Finding b = finding("MISSING_TIMEOUT", "Execution safety", "jobs.build", "timeout-minutes not set");

		String hashA = FindingHasher.hash(a);

		assertThat(hashA).hasSize(64);
		assertThat(hashA).matches("[0-9a-f]+");
		assertThat(FindingHasher.hash(b)).isEqualTo(hashA);
	}

	@Test
	void differentRuleIdsProduceDifferentHashes() {
		FindingFixRequest.Finding a = finding("MISSING_TIMEOUT", "Execution safety", "jobs.build", null);
		FindingFixRequest.Finding b = finding("CONTINUE_ON_ERROR_USED", "Execution safety", "jobs.build", null);

		assertThat(FindingHasher.hash(a)).isNotEqualTo(FindingHasher.hash(b));
	}

	@Test
	void handlesNullFieldsWithoutThrowing() {
		FindingFixRequest.Finding finding = new FindingFixRequest.Finding(
				"RULE", null, null, null, null, null, null, null, null, null);

		assertThat(FindingHasher.hash(finding)).hasSize(64);
	}

	private FindingFixRequest.Finding finding(String ruleId, String capability, String location, String evidence) {
		return new FindingFixRequest.Finding(
				ruleId,
				"label",
				"desc",
				"workflow_quality",
				capability,
				"SMELL",
				evidence,
				location,
				null,
				null);
	}
}
