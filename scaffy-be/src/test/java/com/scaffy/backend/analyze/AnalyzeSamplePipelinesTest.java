package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalyzeSamplePipelinesTest {

	private final PipelineAnalyzer analyzer = new PipelineAnalyzer(
			new YamlPipelineParser(),
			new ProviderDetector(),
			List.of(new GitHubActionsParser(), new GitLabCiParser()),
			List.of(new BuildReleaseManagementCapabilityRuleSet()),
			new ScoringEngine());

	@Test
	void samplePipelinesCoverExpectedMaturityShapes() throws IOException {
		List<Sample> samples = List.of(
				new Sample("github-01-test-only-missing.yml", PipelineProvider.GITHUB_ACTIONS, AnalysisStatus.MISSING),
				new Sample("github-02-manual-build-low.yml", PipelineProvider.GITHUB_ACTIONS, AnalysisStatus.PARTIAL),
				new Sample("github-03-node-build-no-artifact.yml", PipelineProvider.GITHUB_ACTIONS, AnalysisStatus.PARTIAL),
				new Sample("github-04-node-build-with-artifact.yml", PipelineProvider.GITHUB_ACTIONS, AnalysisStatus.PARTIAL),
				new Sample("gitlab-05-docker-build-no-explicit-deps.yml", PipelineProvider.GITLAB_CI, AnalysisStatus.PARTIAL),
				new Sample("gitlab-06-manual-java-build.yml", PipelineProvider.GITLAB_CI, AnalysisStatus.PARTIAL),
				new Sample("gitlab-07-java-build-complete.yml", PipelineProvider.GITLAB_CI, AnalysisStatus.PARTIAL),
				new Sample("gitlab-08-dotnet-build-complete.yml", PipelineProvider.GITLAB_CI, AnalysisStatus.PARTIAL));

		for (Sample sample : samples) {
			AnalysisResponse response = analyzer.analyze(sample.filename(), content(sample.filename()));
			DomainScore build = response.dimensions().getFirst();

			assertThat(response.provider()).as(sample.filename()).isEqualTo(sample.provider());
			assertThat(build.status()).as(sample.filename()).isEqualTo(sample.status());
		}
	}

	private String content(String filename) throws IOException {
		return Files.readString(Path.of("src/test/resources/analyze-samples", filename));
	}

	private record Sample(
			String filename,
			PipelineProvider provider,
			AnalysisStatus status) {
	}
}
