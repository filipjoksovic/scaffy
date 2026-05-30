package com.scaffy.backend.recommend;

public record FindingFixEdit(
		String mode,
		Integer afterLine,
		Integer startLine,
		Integer endLine,
		String code) {
}
