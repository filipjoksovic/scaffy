package com.scaffy.backend.auth;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthCookieService authCookieService;

	public AuthController(AuthCookieService authCookieService) {
		this.authCookieService = authCookieService;
	}

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	public CurrentUserResponse me(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return CurrentUserResponse.from(principal.user());
	}

	@PostMapping(path = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
	public LogoutResponse logout(HttpServletResponse response) {
		authCookieService.clearAccessCookie(response);
		return new LogoutResponse(true);
	}

	public record CurrentUserResponse(
			String id,
			String email,
			String displayName,
			String avatarUrl) {

		static CurrentUserResponse from(AppUser user) {
			return new CurrentUserResponse(
					user.id().toString(),
					user.email(),
					user.displayName(),
					user.avatarUrl());
		}
	}

	public record LogoutResponse(boolean ok) {
	}
}
