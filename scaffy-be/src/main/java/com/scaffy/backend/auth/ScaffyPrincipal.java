package com.scaffy.backend.auth;

import java.security.Principal;
import java.util.UUID;

public record ScaffyPrincipal(
		UUID userId,
		String email,
		String displayName,
		String avatarUrl) implements Principal {

	@Override
	public String getName() {
		return userId.toString();
	}

	public AppUser user() {
		return new AppUser(userId, email, displayName, avatarUrl);
	}
}
