package com.scaffy.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

@SpringBootTest
class AuthControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	void meReturnsUnauthorizedWithoutJwtCookie() throws Exception {
		mockMvc().perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsCurrentUserFromJwtCookie() throws Exception {
		AppUser user = new AppUser(
				UUID.fromString("21a8c717-4598-426c-914d-a8053b2e8f5b"),
				"dev@example.com",
				"Dev User",
				"https://example.com/avatar.png");
		insertUser(user);
		String token = jwtService.createAccessToken(user);

		mockMvc().perform(get("/api/auth/me").cookie(new Cookie(AuthProperties.ACCESS_COOKIE, token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.id().toString()))
				.andExpect(jsonPath("$.email").value("dev@example.com"))
				.andExpect(jsonPath("$.displayName").value("Dev User"))
				.andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.png"));
	}

	@Test
	void meRejectsJwtCookieForMissingUser() throws Exception {
		AppUser user = new AppUser(
				UUID.fromString("3d60ec46-0399-49db-9145-a065f5f3a1d0"),
				"stale@example.com",
				"Stale User",
				null);
		String token = jwtService.createAccessToken(user);

		mockMvc().perform(get("/api/auth/me").cookie(new Cookie(AuthProperties.ACCESS_COOKIE, token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutClearsAccessCookie() throws Exception {
		mockMvc().perform(post("/api/auth/logout"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(header().string("Set-Cookie", containsString(AuthProperties.ACCESS_COOKIE + "=")))
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
				.andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
	}

	private void insertUser(AppUser user) {
		Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, user.id());
		if (existing == null || existing == 0) {
			jdbcTemplate.update("""
					INSERT INTO users (id, email, display_name, avatar_url)
					VALUES (?, ?, ?, ?)
					""", user.id(), user.email(), user.displayName(), user.avatarUrl());
		}
	}
}
