package com.scaffy.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
	};

	private final AuthProperties authProperties;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	public JwtService(AuthProperties authProperties, ObjectMapper objectMapper) {
		this(authProperties, objectMapper, Clock.systemUTC());
	}

	JwtService(AuthProperties authProperties, ObjectMapper objectMapper, Clock clock) {
		this.authProperties = authProperties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public String createAccessToken(AppUser user) {
		Instant now = clock.instant();
		Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
		Map<String, Object> claims = Map.of(
				"sub", user.id().toString(),
				"email", nullable(user.email()),
				"displayName", nullable(user.displayName()),
				"avatarUrl", nullable(user.avatarUrl()),
				"iat", now.getEpochSecond(),
				"exp", now.plus(authProperties.accessTokenTtl()).getEpochSecond());

		String unsigned = base64Url(json(header)) + "." + base64Url(json(claims));
		return unsigned + "." + base64Url(hmac(unsigned));
	}

	public String createRefreshToken(AppUser user) {
		Instant now = clock.instant();
		Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
		Map<String, Object> claims = Map.of(
				"sub", user.id().toString(),
				"typ", "refresh",
				"iat", now.getEpochSecond(),
				"exp", now.plus(authProperties.refreshTokenTtl()).getEpochSecond());

		String unsigned = base64Url(json(header)) + "." + base64Url(json(claims));
		return unsigned + "." + base64Url(hmac(unsigned));
	}

	/** Validates a refresh token (signature, expiry, type) and returns the user id it belongs to. */
	public Optional<UUID> parseRefreshToken(String token) {
		Map<String, Object> claims = verifiedClaims(token);
		if (claims == null || !"refresh".equals(string(claims.get("typ")))) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(string(claims.get("sub"))));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.empty();
		}
	}

	public Optional<ScaffyPrincipal> parseAccessToken(String token) {
		Map<String, Object> claims = verifiedClaims(token);
		if (claims == null || "refresh".equals(string(claims.get("typ")))) {
			return Optional.empty();
		}
		try {
			UUID userId = UUID.fromString(string(claims.get("sub")));
			return Optional.of(new ScaffyPrincipal(
					userId,
					string(claims.get("email")),
					string(claims.get("displayName")),
					string(claims.get("avatarUrl"))));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/** Verifies signature + expiry and returns the decoded claims, or null if the token is invalid. */
	private Map<String, Object> verifiedClaims(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return null;
		}
		String unsigned = parts[0] + "." + parts[1];
		byte[] expected = hmac(unsigned);
		byte[] actual;
		try {
			actual = Base64.getUrlDecoder().decode(parts[2]);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		if (!MessageDigest.isEqual(expected, actual)) {
			return null;
		}
		try {
			Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), CLAIMS_TYPE);
			if (epochSecond(claims.get("exp")) <= clock.instant().getEpochSecond()) {
				return null;
			}
			return claims;
		}
		catch (IllegalArgumentException | JacksonException ex) {
			return null;
		}
	}

	private Object nullable(String value) {
		return value == null ? "" : value;
	}

	private long epochSecond(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(value));
	}

	private String string(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? null : text;
	}

	private byte[] json(Object value) {
		try {
			return objectMapper.writeValueAsBytes(value);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("JWT payload could not be encoded.", ex);
		}
	}

	private String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private byte[] hmac(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new IllegalStateException("JWT signature could not be created.", ex);
		}
	}
}
