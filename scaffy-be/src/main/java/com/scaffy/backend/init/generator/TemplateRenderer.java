package com.scaffy.backend.init.generator;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Lightweight {{name}} substitution. Intentionally not Mustache-compatible —
 * we only need flat string variables for scaffold templates and want zero
 * extra dependencies.
 */
@Component
public class TemplateRenderer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}");

	public String render(String template, Map<String, String> variables) {
		Matcher m = PLACEHOLDER.matcher(template);
		StringBuilder out = new StringBuilder(template.length());
		while (m.find()) {
			String key = m.group(1);
			String value = variables.get(key);
			if (value == null) {
				throw new IllegalStateException("Missing template variable: " + key);
			}
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		m.appendTail(out);
		return out.toString();
	}
}
