package com.scaffy.backend.analyze;

public record SourceSpan(
		String path,
		int startLine,
		int startColumn,
		int endLine,
		int endColumn) {
}
