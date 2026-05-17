package com.scaffy.backend.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AnalysisSupport {

	private AnalysisSupport() {
	}

	static int level(double score) {
		if (score == 0.0) {
			return 1;
		}
		if (score < 0.4) {
			return 2;
		}
		if (score < 0.6) {
			return 3;
		}
		if (score < 0.8) {
			return 4;
		}
		return 5;
	}

	static AnalysisStatus status(double score) {
		if (score == 0.0) {
			return AnalysisStatus.MISSING;
		}
		if (score >= 0.8) {
			return AnalysisStatus.COMPLETE;
		}
		return AnalysisStatus.PARTIAL;
	}

	static double round(double score) {
		return Math.round(score * 100.0) / 100.0;
	}

	static boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	static String value(String value) {
		return value == null ? "" : value;
	}

	static String context(PipelineJob job, PipelineStep step) {
		return String.join(
				" ",
				value(job.id()),
				value(job.name()),
				value(job.stage()),
				value(job.condition()),
				value(job.when()),
				value(job.details()),
				value(step.name()),
				value(step.condition()),
				value(step.details()),
				value(step.command()),
				value(step.uses()));
	}

	@SafeVarargs
	static <T> List<T> distinct(List<T>... groups) {
		List<T> values = new ArrayList<>();
		for (List<T> group : groups) {
			for (T value : group) {
				if (!values.contains(value)) {
					values.add(value);
				}
			}
		}
		return values;
	}
}
