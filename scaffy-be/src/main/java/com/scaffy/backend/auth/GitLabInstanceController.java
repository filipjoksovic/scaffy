package com.scaffy.backend.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/auth/gitlab/instances")
public class GitLabInstanceController {

	private final OAuthInstanceRepository instanceRepository;

	public GitLabInstanceController(OAuthInstanceRepository instanceRepository) {
		this.instanceRepository = instanceRepository;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<OAuthInstanceSummary> list() {
		return instanceRepository.listPublic();
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public AddInstanceResponse add(@RequestBody AddInstanceRequest request) {
		URI uri = parseBaseUrl(request.baseUrl());
		String host = uri.getHost().toLowerCase();
		String baseUrl = canonicalBaseUrl(uri);
		String clientId = requireText(request.clientId(), "clientId");
		String clientSecret = requireText(request.clientSecret(), "clientSecret");
		String displayName = request.displayName() == null || request.displayName().isBlank()
				? host
				: request.displayName().trim();

		OAuthInstance instance = instanceRepository.upsertByHost(baseUrl, host, displayName, clientId, clientSecret);
		String callbackUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/login/oauth2/code/")
				.path(instance.registrationId())
				.toUriString();
		String loginPath = "/oauth2/authorization/" + instance.registrationId();
		return new AddInstanceResponse(OAuthInstanceSummary.from(instance), loginPath, callbackUrl);
	}

	private URI parseBaseUrl(String value) {
		String text = requireText(value, "baseUrl").trim();
		URI uri;
		try {
			uri = new URI(text);
		}
		catch (URISyntaxException ex) {
			throw badRequest("baseUrl must be a valid URL.");
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
			throw badRequest("baseUrl must start with http:// or https://.");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw badRequest("baseUrl must include a host.");
		}
		return uri;
	}

	private String canonicalBaseUrl(URI uri) {
		StringBuilder builder = new StringBuilder()
				.append(uri.getScheme().toLowerCase())
				.append("://")
				.append(uri.getHost().toLowerCase());
		if (uri.getPort() != -1) {
			builder.append(':').append(uri.getPort());
		}
		return builder.toString();
	}

	private String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw badRequest(field + " is required.");
		}
		return value.trim();
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	public record AddInstanceRequest(
			String baseUrl,
			String clientId,
			String clientSecret,
			String displayName) {
	}

	public record AddInstanceResponse(
			OAuthInstanceSummary instance,
			String loginPath,
			String callbackUrl) {
	}
}
