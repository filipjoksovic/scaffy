package com.scaffy.backend.repository;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

@SpringBootTest
class RepositoryAnalysisPersistenceControllerTest {

	private static final AtomicInteger WORKFLOW_FETCHES = new AtomicInteger();

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

	@BeforeEach
	void resetFakeClient() {
		WORKFLOW_FETCHES.set(0);
	}

	@Test
	void analyzeCreatesRunHistoryAndLatestSummary() throws Exception {
		Cookie cookie = authCookie("b1ec1bfe-40b7-4fc3-9425-ad111b423200");
		String repositoryId = connectRepository(cookie);

		mockMvc().perform(post("/api/repositories/" + repositoryId + "/analyze").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runId").exists())
				.andExpect(jsonPath("$.repositoryId").value(repositoryId))
				.andExpect(jsonPath("$.runNumber").value(1))
				.andExpect(jsonPath("$.workflowPath").value(".github/workflows/ci.yml"))
				.andExpect(jsonPath("$.workflowContentHash").exists())
				.andExpect(jsonPath("$.workflowContent").value(containsString("name: CI")))
				.andExpect(jsonPath("$.analyzedAt").exists())
				.andExpect(jsonPath("$.analysisSchemaVersion").value(1))
				.andExpect(jsonPath("$.analyzerModelVersion").value("capability-analyzer-v1"))
				.andExpect(jsonPath("$.analysis.provider").value("github-actions"))
				.andExpect(jsonPath("$.analysis.dimensions[*].capabilityScores[*].findings[*].source").exists());

		mockMvc().perform(post("/api/repositories/" + repositoryId + "/analyze").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repositoryId").value(repositoryId))
				.andExpect(jsonPath("$.runNumber").value(2))
				.andExpect(jsonPath("$.analyzedAt").exists());

		org.assertj.core.api.Assertions.assertThat(WORKFLOW_FETCHES).hasValue(2);

		mockMvc().perform(get("/api/repositories").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].analysisRunCount").value(2))
				.andExpect(jsonPath("$[0].analysisSummary.runNumber").value(2))
				.andExpect(jsonPath("$[0].analysisSummary.workflowPath").value(".github/workflows/ci.yml"))
				.andExpect(jsonPath("$[0].analysisSummary.overallStatus").exists())
				.andExpect(jsonPath("$[0].analysisSummary.analysisSchemaVersion").value(1))
				.andExpect(jsonPath("$[0].analysisSummary.analyzerModelVersion").value("capability-analyzer-v1"));

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runNumber").value(2));

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis/runs").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].runNumber").value(2))
				.andExpect(jsonPath("$[1].runNumber").value(1));

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis/delta").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andExpect(jsonPath("$.baseRun.runNumber").value(1))
				.andExpect(jsonPath("$.currentRun.runNumber").value(2))
				.andExpect(jsonPath("$.overall.direction").exists())
				.andExpect(jsonPath("$.findingChanges").isArray());

		mockMvc().perform(delete("/api/repositories/" + repositoryId).cookie(cookie))
				.andExpect(status().isNoContent());
		Integer analysisRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM repository_analysis_runs WHERE repository_connection_id = ?",
				Integer.class,
				UUID.fromString(repositoryId));
		org.assertj.core.api.Assertions.assertThat(analysisRows).isZero();
	}

	@Test
	void storedAnalysisEndpointRequiresExistingAnalysisAndOwner() throws Exception {
		Cookie ownerCookie = authCookie("b1ec1bfe-40b7-4fc3-9425-ad111b423201");
		Cookie otherCookie = authCookie("b1ec1bfe-40b7-4fc3-9425-ad111b423202");
		String repositoryId = connectRepository(ownerCookie);

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis").cookie(ownerCookie))
				.andExpect(status().isNotFound());

		mockMvc().perform(post("/api/repositories/" + repositoryId + "/analyze").cookie(ownerCookie))
				.andExpect(status().isOk());

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis").cookie(ownerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repositoryId").value(repositoryId))
				.andExpect(jsonPath("$.runNumber").value(1))
				.andExpect(jsonPath("$.workflowContent").value(containsString("name: CI")))
				.andExpect(jsonPath("$.analysis.dimensions").isArray());

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis/delta").cookie(ownerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasPrevious").value(false))
				.andExpect(jsonPath("$.baseRun").doesNotExist())
				.andExpect(jsonPath("$.currentRun.runNumber").value(1));

		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis").cookie(otherCookie))
				.andExpect(status().isNotFound());
		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis/runs").cookie(otherCookie))
				.andExpect(status().isNotFound());
		mockMvc().perform(get("/api/repositories/" + repositoryId + "/analysis/delta").cookie(otherCookie))
				.andExpect(status().isNotFound());
	}

	private String connectRepository(Cookie cookie) throws Exception {
		MvcResult created = mockMvc().perform(post("/api/repositories")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"repository":"scaffy-labs/demo-app"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
		return body.get("id").asString();
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

	@TestConfiguration
	static class FakeGitHubWorkflowClientConfig {

		@Bean
		@Primary
		GitHubWorkflowClient gitHubWorkflowClient() {
			return new GitHubWorkflowClient((ObjectMapper) null, null, null) {
				@Override
				public GitHubWorkflowFile findWorkflow(UUID userId, RepositoryConnection repository) {
					int fetchCount = WORKFLOW_FETCHES.incrementAndGet();
					if (fetchCount == 1) {
						return new GitHubWorkflowFile(".github/workflows/ci.yml", """
							name: CI
							on:
							  push:
							jobs:
							  build:
							    runs-on: ubuntu-22.04
							    timeout-minutes: 10
							    permissions:
							      contents: read
							    steps:
							      - name: Build
							        run: ./mvnw --batch-mode clean package
							""");
					}
					return new GitHubWorkflowFile(".github/workflows/ci.yml", """
							name: CI
							on:
							  push:
							jobs:
							  build:
							    runs-on: ubuntu-22.04
							    timeout-minutes: 10
							    permissions:
							      contents: read
							    steps:
							      - name: Build
							        run: ./mvnw --batch-mode clean package
							      - name: Test
							        run: ./mvnw --batch-mode test jacoco:report
							""");
				}
			};
		}
	}
}
