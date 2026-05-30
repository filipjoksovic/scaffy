package com.scaffy.backend.repository;

public record GitLabProjectOption(
		String pathWithNamespace,
		String owner,
		String name,
		String url,
		boolean privateRepository) {
}
