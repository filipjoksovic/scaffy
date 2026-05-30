package com.scaffy.backend.auth;

import java.util.Map;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class OAuthProfileExtractor {

	public OAuthProfile extract(String registrationId, String instance, OAuth2User user) {
		Map<String, Object> attributes = user.getAttributes();
		if ("google".equals(registrationId)) {
			return new OAuthProfile(
					"google",
					required(attributes, "sub"),
					string(attributes.get("email")),
					string(attributes.get("name")),
					string(attributes.get("picture")),
					"");
		}
		if ("github".equals(registrationId) || "github-repos".equals(registrationId)) {
			return new OAuthProfile(
					"github",
					required(attributes, "id"),
					string(attributes.get("email")),
					prefer(string(attributes.get("name")), string(attributes.get("login"))),
					string(attributes.get("avatar_url")),
					"");
		}
		if (registrationId != null && registrationId.startsWith("gitlab")) {
			return new OAuthProfile(
					"gitlab",
					required(attributes, "id"),
					string(attributes.get("email")),
					prefer(string(attributes.get("name")), string(attributes.get("username"))),
					string(attributes.get("avatar_url")),
					instance == null ? "" : instance);
		}
		throw new IllegalArgumentException("Unsupported OAuth provider: " + registrationId);
	}

	private String required(Map<String, Object> attributes, String key) {
		String value = string(attributes.get(key));
		if (value == null) {
			throw new IllegalArgumentException("OAuth profile is missing required attribute: " + key);
		}
		return value;
	}

	private String prefer(String first, String fallback) {
		return first == null || first.isBlank() ? fallback : first;
	}

	private String string(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? null : text;
	}
}
