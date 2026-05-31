package com.scaffy.backend.init;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.scaffy.backend.auth.AppUser;
import com.scaffy.backend.auth.AuthProperties;
import com.scaffy.backend.auth.JwtService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
class InitHistoryControllerTest {

	@Autowired private WebApplicationContext context;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbc;

	private AppUser user;
	private AppUser otherUser;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void setUp() {
		user = insertUser(UUID.randomUUID(), "history-user@example.com", "History User");
		otherUser = insertUser(UUID.randomUUID(), "other-history@example.com", "Other History");
		jdbc.update("DELETE FROM initializer_generation_jobs WHERE user_id IN (?, ?)", user.id(), otherUser.id());
	}

	@Test
	void historyRequiresAuthentication() throws Exception {
		mockMvc().perform(get("/api/init/history"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void historyReturnsRecentJobsForCurrentUser() throws Exception {
		insertJob(user.id(), "older-app", "failed", OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
		insertJob(user.id(), "newer-app", "succeeded", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
		insertJob(otherUser.id(), "not-mine", "succeeded", OffsetDateTime.now(ZoneOffset.UTC));

		mockMvc().perform(get("/api/init/history").cookie(jwt(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].projectName").value("newer-app"))
				.andExpect(jsonPath("$[0].stack.frontend").value("React"))
				.andExpect(jsonPath("$[0].stack.backend").value("Spring Boot"))
				.andExpect(jsonPath("$[0].stack.pipeline").value("GitHub Actions"))
				.andExpect(jsonPath("$[0].status").value("succeeded"))
				.andExpect(jsonPath("$[0].createdAt").exists())
				.andExpect(jsonPath("$[1].projectName").value("older-app"));
	}

	@Test
	void historyHonorsLimit() throws Exception {
		insertJob(user.id(), "first-app", "succeeded", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
		insertJob(user.id(), "second-app", "succeeded", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

		mockMvc().perform(get("/api/init/history?limit=1").cookie(jwt(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].projectName").value("second-app"));
	}

	private Cookie jwt(AppUser u) {
		return new Cookie(AuthProperties.ACCESS_COOKIE, jwtService.createAccessToken(u));
	}

	private void insertJob(UUID userId, String projectName, String status, OffsetDateTime createdAt) {
		jdbc.update("""
				INSERT INTO initializer_generation_jobs (
					id,
					user_id,
					status,
					project_name,
					request_json,
					selection_json,
					progress_message,
					created_at
				)
				VALUES (?, ?, ?, ?, '{}', ?, 'Done', ?)
				""", UUID.randomUUID(), userId, status, projectName, selectionJson(), createdAt);
	}

	private String selectionJson() {
		return """
				{
				  "frontend": {
				    "id": "react",
				    "name": "React",
				    "versionId": "19",
				    "versionLabel": "19",
				    "version": "19",
				    "runtimeId": "node-22",
				    "runtimeLabel": "Node 22",
				    "runtime": "node",
				    "runtimeVersion": "22"
				  },
				  "backend": {
				    "id": "spring-boot",
				    "name": "Spring Boot",
				    "versionId": "4.0",
				    "versionLabel": "4.0",
				    "version": "4.0",
				    "runtimeId": "java-21",
				    "runtimeLabel": "Java 21",
				    "runtime": "java",
				    "runtimeVersion": "21"
				  },
				  "pipeline": {
				    "id": "github-actions",
				    "name": "GitHub Actions"
				  },
				  "pipelineMaturity": {
				    "id": "l2",
				    "label": "Team",
				    "description": "Team defaults",
				    "level": 2,
				    "dockerRequired": false
				  },
				  "includeDocker": true
				}
				""";
	}

	private AppUser insertUser(UUID id, String email, String displayName) {
		AppUser u = new AppUser(id, email, displayName, null);
		Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
		if (existing == null || existing == 0) {
			jdbc.update("INSERT INTO users (id, email, display_name) VALUES (?, ?, ?)",
					id, email, displayName);
		}
		return u;
	}
}
