package com.scaffy.backend.auth;

import java.io.IOException;
import java.util.Arrays;

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

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			accessCookie(request)
					.flatMap(jwtService::parseAccessToken)
					.ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(
									principal,
									null,
									AuthorityUtils.createAuthorityList("ROLE_USER"))));
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
