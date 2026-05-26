package com.scaffy.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.auth")
public record AuthProperties(
		String jwtSecret,
		boolean cookieSecure,
		String cookieSameSite,
		String cookieDomain,
		long accessTokenTtlSeconds) {

	public static final String ACCESS_COOKIE = "scaffy_access";

	public Duration accessTokenTtl() {
		return Duration.ofSeconds(accessTokenTtlSeconds);
	}
}
