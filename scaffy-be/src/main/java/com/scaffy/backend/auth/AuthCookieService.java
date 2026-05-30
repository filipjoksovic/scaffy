package com.scaffy.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthCookieService {

	private final AuthProperties authProperties;

	public AuthCookieService(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public void addAccessCookie(HttpServletResponse response, String token) {
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(AuthProperties.ACCESS_COOKIE, token)
				.maxAge(authProperties.accessTokenTtl())
				.build()
				.toString());
	}

	public void clearAccessCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(AuthProperties.ACCESS_COOKIE, "")
				.maxAge(0)
				.build()
				.toString());
	}

	public void addRefreshCookie(HttpServletResponse response, String token) {
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(AuthProperties.REFRESH_COOKIE, token)
				.maxAge(authProperties.refreshTokenTtl())
				.build()
				.toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(AuthProperties.REFRESH_COOKIE, "")
				.maxAge(0)
				.build()
				.toString());
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(authProperties.cookieSecure())
				.sameSite(authProperties.cookieSameSite())
				.path("/");
		if (authProperties.cookieDomain() != null && !authProperties.cookieDomain().isBlank()) {
			builder.domain(authProperties.cookieDomain());
		}
		return builder;
	}
}
