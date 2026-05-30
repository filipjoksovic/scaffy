package com.scaffy.backend.auth;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns an OAuth login/connect failure into a clear redirect back to the frontend with an
 * {@code authError} message. The common self-hosted case is the instance being unreachable from
 * the Scaffy server (VPN / private network / untrusted TLS), so connectivity failures get a
 * message that says exactly that.
 */
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuthLoginFailureHandler.class);

	private final AppProperties appProperties;
	private final AuthProperties authProperties;

	public OAuthLoginFailureHandler(AppProperties appProperties, AuthProperties authProperties) {
		this.appProperties = appProperties;
		this.authProperties = authProperties;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		boolean connect = OAuthConnectController.MODE_VALUE.equals(
				readCookie(request, OAuthConnectController.MODE_COOKIE));
		clearModeCookie(response);

		String message = classify(exception);
		String base = connect ? "/workspace" : "/";
		String target = appProperties.frontendUrl() + base + "?authError="
				+ URLEncoder.encode(message, StandardCharsets.UTF_8);
		log.warn("OAuth {} failed: {}", connect ? "connect" : "login", exception.getMessage());
		response.sendRedirect(target);
	}

	private String classify(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof UnknownHostException) {
				return "The Scaffy server could not resolve that provider (DNS). A self-hosted instance "
						+ "must be reachable from the Scaffy server, not just from your browser.";
			}
			if (t instanceof HttpTimeoutException) {
				return "The Scaffy server timed out connecting to that provider. A self-hosted instance "
						+ "behind a VPN or firewall must be reachable from the Scaffy server.";
			}
			if (t instanceof ConnectException || t instanceof NoRouteToHostException) {
				return "The Scaffy server could not reach that provider (connection refused / no route). "
						+ "A self-hosted instance behind a VPN must be reachable from the Scaffy server, not just your browser.";
			}
			if (t instanceof SSLException) {
				return "TLS error connecting to that provider. A self-hosted instance may use a self-signed "
						+ "certificate or a private CA the Scaffy server does not trust.";
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return "Could not complete sign-in with that provider. Please try again.";
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
}
