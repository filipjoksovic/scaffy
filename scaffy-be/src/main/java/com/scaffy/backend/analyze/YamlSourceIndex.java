package com.scaffy.backend.analyze;

import java.util.Map;
import java.util.Optional;

record YamlSourceIndex(Map<String, SourceSpan> spans) {

	SourceSpan sourceFor(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		return Optional.ofNullable(spans.get(path))
				.or(() -> parentPath(path).map(spans::get))
				.orElse(null);
	}

	private Optional<String> parentPath(String path) {
		int dot = path.lastIndexOf('.');
		int bracket = path.lastIndexOf(']');
		int cut = Math.max(dot, bracket);
		if (cut <= 0) {
			return Optional.empty();
		}
		if (path.charAt(cut) == ']') {
			int bracketStart = path.lastIndexOf('[', cut);
			if (bracketStart > 0) {
				return Optional.of(path.substring(0, bracketStart));
			}
		}
		return Optional.of(path.substring(0, cut));
	}
}
