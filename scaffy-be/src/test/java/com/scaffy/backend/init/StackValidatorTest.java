package com.scaffy.backend.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StackValidatorTest {

	private final StackValidator validator = new StackValidator(new StackCatalog());

	@Test
	void acceptsAngularSpringBootGitHubActions() {
		assertThatCode(() -> validator.validate(
				new InitRequest("demo", "angular", "spring-boot", "github-actions")))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsUnsupportedBackend() {
		assertThatThrownBy(() -> validator.validate(
				new InitRequest("demo", "angular", "dotnet", "github-actions")))
				.isInstanceOf(UnsupportedStackException.class)
				.hasMessageContaining("Backend 'dotnet'");
	}

	@Test
	void rejectsUnsupportedPipeline() {
		assertThatThrownBy(() -> validator.validate(
				new InitRequest("demo", "angular", "spring-boot", "jenkins")))
				.isInstanceOf(UnsupportedStackException.class)
				.hasMessageContaining("Pipeline 'jenkins'");
	}
}
