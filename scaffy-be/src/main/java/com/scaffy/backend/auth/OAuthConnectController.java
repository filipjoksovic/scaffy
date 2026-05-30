package com.scaffy.backend.auth;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Account linking ("connect mode"). Unlike login, these flows attach a provider access token to
 * the already-authenticated user without changing identity, so a user who signed in with Google
 * can still link GitHub / GitLab to source repositories.
 */
@RestController
@RequestMapping("/api/auth")
public class OAuthConnectController {

	public static final String MODE_COOKIE = "scaffy_oauth_connect";
	public static final String MODE_VALUE = "connect";

	private final UserRepository userRepository;
	private final AuthProperties authProperties;

	public OAuthConnectController(UserRepository userRepository, AuthProperties authProperties) {
		this.userRepository = userRepository;
		this.authProperties = authProperties;
	}

	@GetMapping("/connect/{registrationId}")
	public void connect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable String registrationId,
			HttpServletResponse response) throws IOException {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		if (!isAllowedRegistration(registrationId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider.");
		}
		response.addHeader(HttpHeaders.SET_COOKIE, modeCookie(MODE_VALUE, 300).toString());
		response.sendRedirect("/oauth2/authorization/" + registrationId);
	}

	@GetMapping(path = "/connections", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ProviderConnectionResponse> connections(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return userRepository.listProviderConnections(principal.userId())
				.stream()
				.filter(connection -> connection.hasToken())
				.filter(connection -> "github".equals(connection.provider()) || "gitlab".equals(connection.provider()))
				.map(ProviderConnectionResponse::from)
				.toList();
	}

	@DeleteMapping("/connections/{provider}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable String provider,
			@RequestParam(value = "instance", required = false) String instance) {
		userRepository.deleteProviderConnection(principal.userId(), provider, instance == null ? "" : instance);
	}

	private boolean isAllowedRegistration(String registrationId) {
		return "github-repos".equals(registrationId)
				|| "gitlab".equals(registrationId)
				|| (registrationId != null && registrationId.startsWith("gitlab-"));
	}

	private ResponseCookie modeCookie(String value, long maxAgeSeconds) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(MODE_COOKIE, value)
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

		static ProviderConnectionResponse from(ProviderConnectionRecord record) {
			List<String> scopeList = record.scopes() == null || record.scopes().isBlank()
					? List.of()
					: List.of(record.scopes().split("[\\s,]+"));
			return new ProviderConnectionResponse(
					record.provider(),
					record.instance(),
					record.displayName(),
					scopeList,
					record.updatedAt());
		}
	}
}
