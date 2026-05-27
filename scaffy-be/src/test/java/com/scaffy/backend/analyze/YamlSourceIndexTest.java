package com.scaffy.backend.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YamlSourceIndexTest {

	private final YamlPipelineParser parser = new YamlPipelineParser();

	@Test
	void mapsGithubActionsYamlPathsToExactSourceSpans() {
		YamlSourceIndex index = parser.sourceIndex("""
				name: CI
				on:
				  push:
				jobs:
				  build:
				    runs-on: ubuntu-24.04
				    steps:
				      - uses: actions/checkout@v4
				      - run: npm test
				""");

		assertThat(index.sourceFor("on.push"))
				.extracting(SourceSpan::path, SourceSpan::startLine, SourceSpan::startColumn)
				.containsExactly("on.push", 3, 3);
		assertThat(index.sourceFor("jobs.build"))
				.extracting(SourceSpan::path, SourceSpan::startLine, SourceSpan::startColumn)
				.containsExactly("jobs.build", 5, 3);
		assertThat(index.sourceFor("jobs.build.steps[0].uses"))
				.extracting(SourceSpan::path, SourceSpan::startLine, SourceSpan::startColumn)
				.containsExactly("jobs.build.steps[0].uses", 8, 9);
		assertThat(index.sourceFor("jobs.build.steps[1].run"))
				.extracting(SourceSpan::path, SourceSpan::startLine, SourceSpan::startColumn)
				.containsExactly("jobs.build.steps[1].run", 9, 9);
	}

	@Test
	void fallsBackToNearestParentPathWhenExactLeafIsNotIndexed() {
		YamlSourceIndex index = parser.sourceIndex("""
				name: CI
				on: [push]
				jobs:
				  build:
				    runs-on: ubuntu-24.04
				""");

		assertThat(index.sourceFor("jobs.build.timeout-minutes"))
				.extracting(SourceSpan::path, SourceSpan::startLine)
				.containsExactly("jobs.build", 4);
	}
}
