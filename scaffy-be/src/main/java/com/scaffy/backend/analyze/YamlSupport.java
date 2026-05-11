package com.scaffy.backend.analyze;

import java.util.Map;
import java.util.Optional;

final class YamlSupport {

	private YamlSupport() {
	}

	static boolean hasKey(Map<?, ?> map, String key) {
		return map != null && map.keySet().stream().anyMatch(candidate -> keyMatches(candidate, key));
	}

	static Optional<Object> value(Map<?, ?> map, String key) {
		if (map == null) {
			return Optional.empty();
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (keyMatches(entry.getKey(), key)) {
				return Optional.ofNullable(entry.getValue());
			}
		}
		return Optional.empty();
	}

	static Optional<Map<?, ?>> mapValue(Map<?, ?> map, String key) {
		return value(map, key)
				.filter(Map.class::isInstance)
				.map(Map.class::cast);
	}

	static Optional<String> stringValue(Map<?, ?> map, String key) {
		return value(map, key).map(YamlSupport::asString);
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
}
