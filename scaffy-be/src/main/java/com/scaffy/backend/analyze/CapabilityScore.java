package com.scaffy.backend.analyze;

import java.util.List;

public record CapabilityScore(
		String capability,
		int points,
		List<CapabilityFinding> findings) {
}
