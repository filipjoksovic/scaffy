package com.scaffy.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.auth")
public record AuthProperties(
		String jwtSecret,
		boolean cookieSecure,
		String cookieSameSite,
		String cookieDomain,
		String providerTokenEncryptionSecret,
		long accessTokenTtlSeconds,
		long refreshTokenTtlSeconds) {

	public static final String ACCESS_COOKIE = "scaffy_access";
	public static final String REFRESH_COOKIE = "scaffy_refresh";

	public Duration accessTokenTtl() {
		return Duration.ofSeconds(accessTokenTtlSeconds);
	}

	public Duration refreshTokenTtl() {
		return Duration.ofSeconds(refreshTokenTtlSeconds > 0 ? refreshTokenTtlSeconds : 2_592_000L);
	}
}
