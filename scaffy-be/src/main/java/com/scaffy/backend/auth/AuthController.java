package com.scaffy.backend.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthCookieService authCookieService;
	private final JwtService jwtService;
	private final UserRepository userRepository;

	public AuthController(AuthCookieService authCookieService, JwtService jwtService, UserRepository userRepository) {
		this.authCookieService = authCookieService;
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	public CurrentUserResponse me(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return CurrentUserResponse.from(principal.user());
	}

	/**
	 * Exchanges a valid refresh cookie for a fresh access cookie (and a renewed, sliding refresh
	 * cookie). Called by the client when an access token has expired, so users stay signed in
	 * without re-running the OAuth flow.
	 */
	@PostMapping(path = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
	public CurrentUserResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = readCookie(request, AuthProperties.REFRESH_COOKIE);
		UUID userId = jwtService.parseRefreshToken(refreshToken).orElse(null);
		AppUser user = userId == null ? null : userRepository.findById(userId).orElse(null);
		if (user == null) {
			authCookieService.clearAccessCookie(response);
			authCookieService.clearRefreshCookie(response);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired.");
		}
		authCookieService.addAccessCookie(response, jwtService.createAccessToken(user));
		authCookieService.addRefreshCookie(response, jwtService.createRefreshToken(user));
		return CurrentUserResponse.from(user);
	}

	@PostMapping(path = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
	public LogoutResponse logout(HttpServletResponse response) {
		authCookieService.clearAccessCookie(response);
		authCookieService.clearRefreshCookie(response);
		return new LogoutResponse(true);
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
