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
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(token)
				.maxAge(authProperties.accessTokenTtl())
				.build()
				.toString());
	}

	public void clearAccessCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, baseCookie("")
				.maxAge(0)
				.build()
				.toString());
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AuthProperties.ACCESS_COOKIE, value)
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
