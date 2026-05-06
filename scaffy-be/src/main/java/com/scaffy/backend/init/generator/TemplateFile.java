package com.scaffy.backend.init.generator;

/**
 * Pairs a classpath template with the path it should occupy in the generated
 * ZIP. Both the template body and the destination path are subject to
 * {{variable}} substitution when {@code render} is true.
 */
public record TemplateFile(String resourcePath, String destinationPath, boolean render) {

	public static TemplateFile rendered(String resourcePath, String destinationPath) {
		return new TemplateFile(resourcePath, destinationPath, true);
	}

	public static TemplateFile copy(String resourcePath, String destinationPath) {
		return new TemplateFile(resourcePath, destinationPath, false);
	}
}
