package com.scaffy.backend.auth;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.workspace.WorkspaceService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Account linking ("connect mode"), scoped to a workspace. A user connects GitHub/GitLab separately
 * per workspace; the token is stored against (workspace, user). The browser navigates here (so the
 * workspace is passed as a query param, not the X-Workspace-Id header which only rides on fetch).
 */
@RestController
@RequestMapping("/api/auth")
public class OAuthConnectController {

	public static final String MODE_COOKIE = "scaffy_oauth_connect";
	public static final String MODE_VALUE = "connect";
	public static final String WORKSPACE_COOKIE = "scaffy_oauth_ws";
	public static final String RETURN_COOKIE = "scaffy_oauth_return";
	static final String WORKSPACE_HEADER = "X-Workspace-Id";

	private final WorkspaceOAuthTokenRepository tokenRepository;
	private final WorkspaceService workspaceService;
	private final AuthProperties authProperties;

	public OAuthConnectController(
			WorkspaceOAuthTokenRepository tokenRepository,
			WorkspaceService workspaceService,
			AuthProperties authProperties) {
		this.tokenRepository = tokenRepository;
		this.workspaceService = workspaceService;
		this.authProperties = authProperties;
	}

	@GetMapping("/connect/{registrationId}")
	public void connect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable String registrationId,
			@RequestParam(value = "workspace", required = false) UUID workspace,
			@RequestParam(value = "return", required = false) String returnTo,
			HttpServletResponse response) throws IOException {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		if (!isAllowedRegistration(registrationId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider.");
		}
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspace);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(MODE_COOKIE, MODE_VALUE, 300).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(WORKSPACE_COOKIE, activeWorkspace.toString(), 300).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(RETURN_COOKIE, sanitizeReturn(returnTo), 300).toString());
		response.sendRedirect("/oauth2/authorization/" + registrationId);
	}

	@GetMapping(path = "/connections", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ProviderConnectionResponse> connections(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		return tokenRepository.listConnections(activeWorkspace, principal.userId())
				.stream()
				.filter(connection -> "github".equals(connection.provider()) || "gitlab".equals(connection.provider()))
				.map(ProviderConnectionResponse::from)
				.toList();
	}

	@DeleteMapping("/connections/{provider}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = WORKSPACE_HEADER, required = false) UUID workspaceId,
			@PathVariable String provider,
			@RequestParam(value = "instance", required = false) String instance) {
		UUID activeWorkspace = workspaceService.resolveActiveWorkspace(principal.userId(), workspaceId);
		tokenRepository.delete(activeWorkspace, principal.userId(), provider, instance == null ? "" : instance);
	}

	private boolean isAllowedRegistration(String registrationId) {
		return "github-repos".equals(registrationId)
				|| "gitlab".equals(registrationId)
				|| (registrationId != null && registrationId.startsWith("gitlab-"));
	}

	/** Only allow returning to an in-app path, never an absolute/protocol-relative URL. */
	private String sanitizeReturn(String returnTo) {
		if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
			return "/dashboard";
		}
		return returnTo;
	}

	private ResponseCookie cookie(String name, String value, long maxAgeSeconds) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(authProperties.cookieSecure())
				.sameSite(authProperties.cookieSameSite())
				.path("/")
				.maxAge(maxAgeSeconds);
		if (authProperties.cookieDomain() != null && !authProperties.cookieDomain().isBlank()) {
			builder.domain(authProperties.cookieDomain());
		}
		return builder.build();
	}

	public record ProviderConnectionResponse(
			String provider,
			String instance,
			String displayName,
			List<String> scopes,
			OffsetDateTime connectedAt) {

		static ProviderConnectionResponse from(ProviderConnectionRecord connection) {
			List<String> scopeList = connection.scopes() == null || connection.scopes().isBlank()
					? List.of()
					: List.of(connection.scopes().split("[\\s,]+"));
			return new ProviderConnectionResponse(
					connection.provider(),
					connection.instance(),
					connection.displayName(),
					scopeList,
					connection.updatedAt());
		}
	}
}
