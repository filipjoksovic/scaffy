package com.scaffy.backend.init;

import org.springframework.stereotype.Component;

@Component
public class StackValidator {

	private final StackCatalog catalog;

	public StackValidator(StackCatalog catalog) {
		this.catalog = catalog;
	}

	public void validate(InitRequest request) {
		if (!catalog.supportsFrontend(request.frontend())) {
			throw new UnsupportedStackException(
					"Frontend '" + request.frontend() + "' is not supported in iteration 1.");
		}
		if (!catalog.supportsBackend(request.backend())) {
			throw new UnsupportedStackException(
					"Backend '" + request.backend() + "' is not supported in iteration 1.");
		}
		if (!catalog.supportsPipeline(request.pipeline())) {
			throw new UnsupportedStackException(
					"Pipeline '" + request.pipeline() + "' is not supported. "
							+ "Choose 'github-actions' or 'gitlab-ci'.");
		}
	}
}
