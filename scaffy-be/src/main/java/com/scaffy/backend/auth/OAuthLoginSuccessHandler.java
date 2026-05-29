package com.scaffy.backend.auth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

	private final AppProperties appProperties;
	private final AuthCookieService authCookieService;
	private final JwtService jwtService;
	private final OAuth2AuthorizedClientService authorizedClientService;
	private final OAuthProfileExtractor profileExtractor;
	private final ProviderTokenCrypto providerTokenCrypto;
	private final UserRepository userRepository;
	private final OAuthInstanceRepository instanceRepository;

	public OAuthLoginSuccessHandler(
			AppProperties appProperties,
			AuthCookieService authCookieService,
			JwtService jwtService,
			OAuth2AuthorizedClientService authorizedClientService,
			OAuthProfileExtractor profileExtractor,
			ProviderTokenCrypto providerTokenCrypto,
			UserRepository userRepository,
			OAuthInstanceRepository instanceRepository) {
		this.appProperties = appProperties;
		this.authCookieService = authCookieService;
		this.jwtService = jwtService;
		this.authorizedClientService = authorizedClientService;
		this.profileExtractor = profileExtractor;
		this.providerTokenCrypto = providerTokenCrypto;
		this.userRepository = userRepository;
		this.instanceRepository = instanceRepository;
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
		AppUser user = userRepository.upsertOAuthUser(profile);
		log.info(
				"OAuth login succeeded provider={} providerUserId={} userId={} emailPresent={}",
				profile.provider(),
				profile.providerUserId(),
				user.id(),
				profile.email() != null && !profile.email().isBlank());
		persistProviderToken(oauth, profile, user);
		authCookieService.addAccessCookie(response, jwtService.createAccessToken(user));
		log.info("Issued Scaffy auth cookie for userId={} redirect={}", user.id(), appProperties.frontendUrl());
		response.sendRedirect(appProperties.frontendUrl());
	}

	private void persistProviderToken(OAuth2AuthenticationToken oauth, OAuthProfile profile, AppUser user) {
		OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
				oauth.getAuthorizedClientRegistrationId(),
				oauth.getName());
		if (client == null || client.getAccessToken() == null) {
			log.warn(
					"OAuth access token was not available after login provider={} providerUserId={} userId={} oauthName={}",
					profile.provider(),
					profile.providerUserId(),
					user.id(),
					oauth.getName());
			return;
		}
		OAuth2AccessToken accessToken = client.getAccessToken();
		int updatedRows = userRepository.updateOAuthAccessToken(
				user.id(),
				profile.provider(),
				profile.instance(),
				profile.providerUserId(),
				providerTokenCrypto.encrypt(accessToken.getTokenValue()),
				accessToken.getExpiresAt(),
				accessToken.getScopes());
		log.info(
				"Persisted OAuth access token provider={} providerUserId={} userId={} updatedRows={} expiresAt={} scopes={}",
				profile.provider(),
				profile.providerUserId(),
				user.id(),
				updatedRows,
				accessToken.getExpiresAt(),
				accessToken.getScopes());
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
