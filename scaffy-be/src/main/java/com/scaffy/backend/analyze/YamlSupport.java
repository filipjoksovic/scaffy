package com.scaffy.backend.analyze;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class YamlSupport {

	private YamlSupport() {
	}

	static boolean hasKey(Map<Object, Object> map, String key) {
		return map != null && map.keySet().stream().anyMatch(candidate -> keyMatches(candidate, key));
	}

	static Optional<Object> value(Map<Object, Object> map, String key) {
		if (map == null) {
			return Optional.empty();
		}
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			if (keyMatches(entry.getKey(), key)) {
				return Optional.ofNullable(entry.getValue());
			}
		}
		return Optional.empty();
	}

	static Optional<Map<Object, Object>> mapValue(Map<Object, Object> map, String key) {
		return value(map, key)
				.flatMap(YamlSupport::asMap);
	}

	static Optional<String> stringValue(Map<Object, Object> map, String key) {
		return value(map, key).map(YamlSupport::asString);
	}

	static Optional<Map<Object, Object>> asMap(Object value) {
		if (!(value instanceof Map)) {
			return Optional.empty();
		}
		return Optional.of(castMap(value));
	}

	static Optional<List<Object>> asList(Object value) {
		if (!(value instanceof List)) {
			return Optional.empty();
		}
		return Optional.of(castList(value));
	}

	static String asString(Object value) {
		if (value == null) {
			return null;
		}
		return String.valueOf(value);
	}

	private static boolean keyMatches(Object candidate, String key) {
		if (candidate instanceof String stringKey) {
			return stringKey.equals(key);
		}
		return "on".equals(key) && candidate instanceof Boolean booleanKey && booleanKey;
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> castMap(Object value) {
		return (Map<Object, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> castList(Object value) {
		return (List<Object>) value;
	}
}
