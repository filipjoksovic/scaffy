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
			clearModeCookie(response);
			if (currentUserId != null) {
				linkProviderToken(oauth, profile, currentUserId);
				log.info(
						"Linked provider account provider={} instance={} userId={}",
						profile.provider(),
						profile.instance(),
						currentUserId);
				response.sendRedirect(connectRedirect(profile));
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
		log.info("Issued Scaffy auth cookie for userId={} redirect={}", user.id(), appProperties.frontendUrl());
		response.sendRedirect(appProperties.frontendUrl());
	}

	private void linkProviderToken(OAuth2AuthenticationToken oauth, OAuthProfile profile, UUID userId) {
		OAuth2AccessToken accessToken = loadAccessToken(oauth, profile);
		if (accessToken == null) {
			return;
		}
		userRepository.linkOAuthAccount(
				userId,
				profile,
				providerTokenCrypto.encrypt(accessToken.getTokenValue()),
				accessToken.getExpiresAt(),
				accessToken.getScopes());
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

	private String connectRedirect(OAuthProfile profile) {
		StringBuilder url = new StringBuilder(appProperties.frontendUrl())
				.append("/dashboard?connected=")
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

	private void clearModeCookie(HttpServletResponse response) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(OAuthConnectController.MODE_COOKIE, "")
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
