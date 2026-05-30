package com.scaffy.backend.auth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.scaffy.backend.workspace.WorkspaceService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

	private final AppProperties appProperties;
	private final AuthProperties authProperties;
	private final AuthCookieService authCookieService;
	private final JwtService jwtService;
	private final OAuth2AuthorizedClientService authorizedClientService;
	private final OAuthProfileExtractor profileExtractor;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final UserRepository userRepository;
	private final WorkspaceOAuthTokenRepository workspaceTokenRepository;
	private final OAuthInstanceRepository instanceRepository;
	private final WorkspaceService workspaceService;

	public OAuthLoginSuccessHandler(
			AppProperties appProperties,
			AuthProperties authProperties,
			AuthCookieService authCookieService,
			JwtService jwtService,
			OAuth2AuthorizedClientService authorizedClientService,
			OAuthProfileExtractor profileExtractor,
			ProviderTokenCrypto providerTokenCrypto,
			UserRepository userRepository,
			WorkspaceOAuthTokenRepository workspaceTokenRepository,
			OAuthInstanceRepository instanceRepository,
			WorkspaceService workspaceService) {
		this.appProperties = appProperties;
		this.authProperties = authProperties;
		this.authCookieService = authCookieService;
		this.jwtService = jwtService;
		this.authorizedClientService = authorizedClientService;
		this.profileExtractor = profileExtractor;
		this.providerTokenCrypto = providerTokenCrypto;
		this.userRepository = userRepository;
		this.workspaceTokenRepository = workspaceTokenRepository;
		this.instanceRepository = instanceRepository;
		this.workspaceService = workspaceService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
		String registrationId = oauth.getAuthorizedClientRegistrationId();
		OAuthProfile profile = profileExtractor.extract(
				registrationId,
				resolveInstanceHost(registrationId),
				(OAuth2User) oauth.getPrincipal());

		if (OAuthConnectController.MODE_VALUE.equals(readCookie(request, OAuthConnectController.MODE_COOKIE))) {
			UUID currentUserId = currentUserId(request);
			UUID workspaceId = parseUuid(readCookie(request, OAuthConnectController.WORKSPACE_COOKIE));
			String returnPath = sanitizeReturn(readCookie(request, OAuthConnectController.RETURN_COOKIE));
			clearConnectCookies(response);
			if (currentUserId != null) {
				if (workspaceId != null && isMember(workspaceId, currentUserId)) {
					linkProviderToken(oauth, profile, workspaceId, currentUserId);
					log.info(
							"Linked provider account provider={} instance={} userId={} workspaceId={}",
							profile.provider(),
							profile.instance(),
							currentUserId,
							workspaceId);
					response.sendRedirect(connectRedirect(returnPath, profile));
				}
				else {
					log.warn("Connect-mode OAuth missing/invalid workspace for userId={}", currentUserId);
					response.sendRedirect(appProperties.frontendUrl()
							+ "/dashboard?authError="
							+ URLEncoder.encode("Could not determine the workspace to connect.", StandardCharsets.UTF_8));
				}
				return;
			}
			log.warn("Connect-mode OAuth completed without a valid current session; falling back to login");
		}

		AppUser user = userRepository.upsertOAuthUser(profile);
		log.info(
				"OAuth login succeeded provider={} providerUserId={} userId={} emailPresent={}",
				profile.provider(),
				profile.providerUserId(),
				user.id(),
				profile.email() != null && !profile.email().isBlank());
		workspaceService.onLogin(user);
		// Login establishes identity only. Repository access (and its token) is granted later via an
		// explicit "Connect" step, so we intentionally do not persist the provider token here.
		authCookieService.addAccessCookie(response, jwtService.createAccessToken(user));
		authCookieService.addRefreshCookie(response, jwtService.createRefreshToken(user));
		log.info("Issued Scaffy auth cookie for userId={} redirect={}", user.id(), appProperties.frontendUrl());
		response.sendRedirect(appProperties.frontendUrl());
	}

	private void linkProviderToken(OAuth2AuthenticationToken oauth, OAuthProfile profile, UUID workspaceId, UUID userId) {
		OAuth2AccessToken accessToken = loadAccessToken(oauth, profile);
		if (accessToken == null) {
			return;
		}
		workspaceTokenRepository.upsert(
				workspaceId,
				userId,
				profile.provider(),
				profile.instance(),
				profile.providerUserId(),
				profile.displayName(),
				providerTokenCrypto.encrypt(accessToken.getTokenValue()),
				accessToken.getExpiresAt(),
				accessToken.getScopes());
	}

	private boolean isMember(UUID workspaceId, UUID userId) {
		try {
			workspaceService.requireMembership(workspaceId, userId);
			return true;
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private String sanitizeReturn(String returnTo) {
		if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
			return "/dashboard";
		}
		return returnTo;
	}

	private OAuth2AccessToken loadAccessToken(OAuth2AuthenticationToken oauth, OAuthProfile profile) {
		OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
				oauth.getAuthorizedClientRegistrationId(),
				oauth.getName());
		if (client == null || client.getAccessToken() == null) {
			log.warn(
					"OAuth access token was not available provider={} providerUserId={} oauthName={}",
					profile.provider(),
					profile.providerUserId(),
					oauth.getName());
			return null;
		}
		return client.getAccessToken();
	}

	private String connectRedirect(String returnPath, OAuthProfile profile) {
		StringBuilder url = new StringBuilder(appProperties.frontendUrl())
				.append(returnPath)
				.append(returnPath.contains("?") ? "&" : "?")
				.append("connected=")
				.append(URLEncoder.encode(profile.provider(), StandardCharsets.UTF_8));
		if (profile.instance() != null && !profile.instance().isBlank()) {
			url.append("&instance=").append(URLEncoder.encode(profile.instance(), StandardCharsets.UTF_8));
		}
		return url.toString();
	}

	private UUID currentUserId(HttpServletRequest request) {
		String accessToken = readCookie(request, AuthProperties.ACCESS_COOKIE);
		if (accessToken == null) {
			return null;
		}
		return jwtService.parseAccessToken(accessToken).map(ScaffyPrincipal::userId).orElse(null);
	}

	private String readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void clearConnectCookies(HttpServletResponse response) {
		clearCookie(response, OAuthConnectController.MODE_COOKIE);
		clearCookie(response, OAuthConnectController.WORKSPACE_COOKIE);
		clearCookie(response, OAuthConnectController.RETURN_COOKIE);
	}

	private void clearCookie(HttpServletResponse response, String name) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
				.httpOnly(true)
				.secure(authProperties.cookieSecure())
				.sameSite(authProperties.cookieSameSite())
				.path("/")
				.maxAge(0);
		if (authProperties.cookieDomain() != null && !authProperties.cookieDomain().isBlank()) {
			builder.domain(authProperties.cookieDomain());
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	private String resolveInstanceHost(String registrationId) {
		if (registrationId == null) {
			return "";
		}
		if (OAuthClientConfig.GITLAB_COM_REGISTRATION_ID.equals(registrationId)) {
			return "gitlab.com";
		}
		if (registrationId.startsWith("gitlab-")) {
			return instanceRepository.findByRegistrationId(registrationId)
					.map(OAuthInstance::host)
					.orElse("");
		}
		return "";
	}
}
