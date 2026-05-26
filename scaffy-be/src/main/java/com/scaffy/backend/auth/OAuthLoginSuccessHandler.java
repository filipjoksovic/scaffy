package com.scaffy.backend.auth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private final AppProperties appProperties;
	private final AuthCookieService authCookieService;
	private final JwtService jwtService;
	private final OAuthProfileExtractor profileExtractor;
	private final UserRepository userRepository;

	public OAuthLoginSuccessHandler(
			AppProperties appProperties,
			AuthCookieService authCookieService,
			JwtService jwtService,
			OAuthProfileExtractor profileExtractor,
			UserRepository userRepository) {
		this.appProperties = appProperties;
		this.authCookieService = authCookieService;
		this.jwtService = jwtService;
		this.profileExtractor = profileExtractor;
		this.userRepository = userRepository;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
		OAuthProfile profile = profileExtractor.extract(
				oauth.getAuthorizedClientRegistrationId(),
				(OAuth2User) oauth.getPrincipal());
		AppUser user = userRepository.upsertOAuthUser(profile);
		authCookieService.addAccessCookie(response, jwtService.createAccessToken(user));
		response.sendRedirect(appProperties.frontendUrl());
	}
}
