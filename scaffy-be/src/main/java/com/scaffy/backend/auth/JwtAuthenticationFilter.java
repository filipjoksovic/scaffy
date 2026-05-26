package com.scaffy.backend.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			Optional<String> token = accessCookie(request);
			if (token.isPresent()) {
				Optional<ScaffyPrincipal> jwtPrincipal = jwtService.parseAccessToken(token.get());
				if (jwtPrincipal.isEmpty()) {
					log.warn("Ignored invalid or expired Scaffy auth cookie path={}", request.getRequestURI());
				}
				jwtPrincipal
						.flatMap(principal -> userRepository.findById(principal.userId())
								.map(user -> new ScaffyPrincipal(user.id(), user.email(), user.displayName(), user.avatarUrl()))
								.or(() -> {
									log.warn("Ignored Scaffy auth cookie for missing userId={} path={}", principal.userId(), request.getRequestURI());
									return Optional.empty();
								}))
						.ifPresent(principal -> {
							log.info("Authenticated request userId={} path={}", principal.userId(), request.getRequestURI());
							SecurityContextHolder.getContext().setAuthentication(
									new UsernamePasswordAuthenticationToken(
											principal,
											null,
											AuthorityUtils.createAuthorityList("ROLE_USER")));
						});
			}
		}
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<String> accessCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return java.util.Optional.empty();
		}
		return Arrays.stream(cookies)
				.filter(cookie -> AuthProperties.ACCESS_COOKIE.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst();
	}
}
