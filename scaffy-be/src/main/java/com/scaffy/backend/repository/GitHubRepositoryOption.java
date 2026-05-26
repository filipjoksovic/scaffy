package com.scaffy.backend.repository;

public record GitHubRepositoryOption(
		String fullName,
		String owner,
		String name,
		String url,
		boolean privateRepository) {
}
