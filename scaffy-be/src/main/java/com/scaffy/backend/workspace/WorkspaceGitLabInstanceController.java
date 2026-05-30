package com.scaffy.backend.workspace;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.scaffy.backend.auth.OAuthInstance;
import com.scaffy.backend.auth.OAuthInstanceRepository;
import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.auth.UserRepository;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/gitlab-instances")
public class WorkspaceGitLabInstanceController {

	private final WorkspaceService workspaceService;
	private final WorkspaceGitLabInstanceRepository instanceRepository;
	private final OAuthInstanceRepository oauthInstanceRepository;
	private final UserRepository userRepository;

	public WorkspaceGitLabInstanceController(
			WorkspaceService workspaceService,
			WorkspaceGitLabInstanceRepository instanceRepository,
			OAuthInstanceRepository oauthInstanceRepository,
			UserRepository userRepository) {
		this.workspaceService = workspaceService;
		this.instanceRepository = instanceRepository;
		this.oauthInstanceRepository = oauthInstanceRepository;
		this.userRepository = userRepository;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<WorkspaceGitLabInstanceResponse> list(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId) {
		workspaceService.requireMembership(workspaceId, principal.userId());
		return instanceRepository.listForWorkspace(workspaceId)
				.stream()
				.map(instance -> WorkspaceGitLabInstanceResponse.from(instance, isConnected(principal.userId(), instance.host())))
				.toList();
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public AddInstanceResponse add(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@RequestBody AddInstanceRequest request) {
		workspaceService.requireOwner(workspaceId, principal.userId());

		URI uri = parseBaseUrl(request.baseUrl());
		String host = uri.getHost().toLowerCase();
		String baseUrl = canonicalBaseUrl(uri);
		String clientId = requireText(request.clientId(), "clientId");
		String clientSecret = requireText(request.clientSecret(), "clientSecret");
		String displayName = request.displayName() == null || request.displayName().isBlank()
				? host
				: request.displayName().trim();

		OAuthInstance oauthInstance = oauthInstanceRepository.upsertByHost(baseUrl, host, displayName, clientId, clientSecret);
		WorkspaceGitLabInstance instance = instanceRepository.add(
				workspaceId,
				oauthInstance.registrationId(),
				host,
				baseUrl,
				displayName,
				principal.userId());

		String callbackUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/login/oauth2/code/")
				.path(oauthInstance.registrationId())
				.toUriString();
		return new AddInstanceResponse(
				WorkspaceGitLabInstanceResponse.from(instance, isConnected(principal.userId(), host)),
				callbackUrl);
	}

	@DeleteMapping("/{instanceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID workspaceId,
			@PathVariable UUID instanceId) {
		workspaceService.requireOwner(workspaceId, principal.userId());
		if (!instanceRepository.delete(workspaceId, instanceId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GitLab instance not found.");
		}
	}

	private boolean isConnected(UUID userId, String host) {
		return userRepository.findOAuthAccessToken(userId, "gitlab", host).isPresent();
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

	public record WorkspaceGitLabInstanceResponse(
			String id,
			String host,
			String baseUrl,
			String displayName,
			String registrationId,
			String connectPath,
			boolean connected) {

		static WorkspaceGitLabInstanceResponse from(WorkspaceGitLabInstance instance, boolean connected) {
			return new WorkspaceGitLabInstanceResponse(
					instance.id().toString(),
					instance.host(),
					instance.baseUrl(),
					instance.displayName(),
					instance.registrationId(),
					"/api/auth/connect/" + instance.registrationId(),
					connected);
		}
	}

	public record AddInstanceResponse(
			WorkspaceGitLabInstanceResponse instance,
			String callbackUrl) {
	}
}
