package com.scaffy.backend.repository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.scaffy.backend.auth.AppUser;
import com.scaffy.backend.auth.AuthProperties;
import com.scaffy.backend.auth.JwtService;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "scaffy.cors.allowed-origins=http://localhost:5173")
class RepositoryConnectionControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	void repositoriesRequireAuthentication() throws Exception {
		mockMvc().perform(get("/api/repositories"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deleteRepositoryCorsPreflightAllowsLocalFrontend() throws Exception {
		mockMvc().perform(options("/api/repositories/a1ec1bfe-40b7-4fc3-9425-ad111b423123")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "DELETE"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void connectsListsAndDeletesGitHubRepository() throws Exception {
		Cookie cookie = authCookie("a1ec1bfe-40b7-4fc3-9425-ad111b423123");

		MvcResult created = mockMvc().perform(post("/api/repositories")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"repository":"https://github.com/Scaffy-Labs/Demo-App.git"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.provider").value("github"))
				.andExpect(jsonPath("$.owner").value("scaffy-labs"))
				.andExpect(jsonPath("$.name").value("demo-app"))
				.andExpect(jsonPath("$.url").value("https://github.com/scaffy-labs/demo-app"))
				.andExpect(jsonPath("$.analysisRunCount").value(0))
				.andExpect(jsonPath("$.analysisSummary").doesNotExist())
				.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
		String id = body.get("id").asString();

		mockMvc().perform(post("/api/repositories")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"repository":"scaffy-labs/demo-app"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(id));

		mockMvc().perform(get("/api/repositories").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].analysisRunCount").value(0));

		mockMvc().perform(delete("/api/repositories/" + id).cookie(cookie))
				.andExpect(status().isNoContent());

		mockMvc().perform(get("/api/repositories").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void rejectsNonGitHubRepositoryReferences() throws Exception {
		mockMvc().perform(post("/api/repositories")
						.cookie(authCookie("a1ec1bfe-40b7-4fc3-9425-ad111b423124"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"repository":"https://gitlab.com/scaffy-labs/demo-app"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void fetchingGitHubRepositoriesRequiresGitHubToken() throws Exception {
		mockMvc().perform(get("/api/repositories/github")
						.cookie(authCookie("a1ec1bfe-40b7-4fc3-9425-ad111b423125")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Connect GitHub in this workspace before fetching repositories."));
	}

	@Test
	void analyzingRepositoryRequiresGitHubToken() throws Exception {
		Cookie cookie = authCookie("a1ec1bfe-40b7-4fc3-9425-ad111b423126");

		MvcResult created = mockMvc().perform(post("/api/repositories")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"repository":"scaffy-labs/demo-app"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
		String id = body.get("id").asString();

		mockMvc().perform(post("/api/repositories/" + id + "/analyze").cookie(cookie))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.repositoryId").value(id))
				.andExpect(jsonPath("$.status").value("queued"));
	}

	private Cookie authCookie(String userId) {
		AppUser user = new AppUser(
				UUID.fromString(userId),
				"dev@example.com",
				"Dev User",
				"https://example.com/avatar.png");
		Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, user.id());
		if (existing == null || existing == 0) {
			jdbcTemplate.update("""
					INSERT INTO users (id, email, display_name, avatar_url)
					VALUES (?, ?, ?, ?)
					""", user.id(), user.email(), user.displayName(), user.avatarUrl());
			UUID workspaceId = UUID.randomUUID();
			jdbcTemplate.update("""
					INSERT INTO workspaces (id, name, slug)
					VALUES (?, ?, ?)
					""", workspaceId, "Dev workspace", "ws-" + workspaceId.toString().replace("-", ""));
			jdbcTemplate.update("""
					INSERT INTO workspace_members (id, workspace_id, user_id, role)
					VALUES (?, ?, ?, 'owner')
					""", UUID.randomUUID(), workspaceId, user.id());
		}
		return new Cookie(AuthProperties.ACCESS_COOKIE, jwtService.createAccessToken(user));
	}
}
