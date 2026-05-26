package com.scaffy.backend.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GitHubRepositoryRefParser {

	private static final Pattern OWNER_REPO = Pattern.compile("(?<owner>[A-Za-z0-9_.-]+)/(?<repo>[A-Za-z0-9_.-]+)");
	private static final Pattern SSH_URL = Pattern.compile("git@github\\.com:(?<owner>[A-Za-z0-9_.-]+)/(?<repo>[A-Za-z0-9_.-]+)(?:\\.git)?");

	public GitHubRepositoryRef parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw invalid();
		}

		String value = trimRepository(raw);
		Matcher ssh = SSH_URL.matcher(value);
		if (ssh.matches()) {
			return ref(ssh.group("owner"), ssh.group("repo"));
		}

		if (!value.contains("://")) {
			Matcher ownerRepo = OWNER_REPO.matcher(value);
			if (ownerRepo.matches()) {
				return ref(ownerRepo.group("owner"), ownerRepo.group("repo"));
			}
			throw invalid();
		}

		try {
			URI uri = new URI(value);
			if (!"github.com".equalsIgnoreCase(uri.getHost())) {
				throw invalid();
			}
			String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
			if (parts.length < 2) {
				throw invalid();
			}
			return ref(parts[0], parts[1]);
		}
		catch (URISyntaxException ex) {
			throw invalid();
		}
	}

	private GitHubRepositoryRef ref(String owner, String repo) {
		String normalizedOwner = normalize(owner);
		String normalizedRepo = normalize(trimRepository(repo));
		if (!OWNER_REPO.matcher(normalizedOwner + "/" + normalizedRepo).matches()) {
			throw invalid();
		}
		return new GitHubRepositoryRef(
				normalizedOwner,
				normalizedRepo,
				"https://github.com/" + normalizedOwner + "/" + normalizedRepo);
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	private String trimRepository(String value) {
		String trimmed = value.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		if (trimmed.endsWith(".git")) {
			trimmed = trimmed.substring(0, trimmed.length() - 4);
		}
		return trimmed;
	}

	private ResponseStatusException invalid() {
		return new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Enter a GitHub repository as owner/repo or https://github.com/owner/repo.");
	}
}
