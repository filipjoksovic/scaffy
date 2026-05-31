package com.scaffy.backend.init.favourite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.scaffy.backend.auth.AppUser;
import com.scaffy.backend.auth.AuthProperties;
import com.scaffy.backend.auth.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class FavouriteStackControllerTest {

	@Autowired private WebApplicationContext context;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private FavouriteStackRepository repository;
	@Autowired private ObjectMapper objectMapper;

	private AppUser user;
	private AppUser otherUser;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void setUp() {
		user      = insertUser(UUID.randomUUID(), "fav-user@example.com",  "Fav User");
		otherUser = insertUser(UUID.randomUUID(), "other-fav@example.com", "Other User");
		jdbc.update("DELETE FROM favourite_stacks WHERE user_id IN (?, ?)", user.id(), otherUser.id());
	}

	private Cookie jwt(AppUser u) {
		return new Cookie(AuthProperties.ACCESS_COOKIE, jwtService.createAccessToken(u));
	}

	// ------------------------------------------------------------------
	// Authentication guard
	// ------------------------------------------------------------------

	@Test
	void listRequiresAuthentication() throws Exception {
		mockMvc().perform(get("/api/init/favourites"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void saveRequiresAuthentication() throws Exception {
		mockMvc().perform(post("/api/init/favourites")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody("My Stack")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deleteRequiresAuthentication() throws Exception {
		mockMvc().perform(delete("/api/init/favourites/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------
	// GET /api/init/favourites
	// ------------------------------------------------------------------

	@Test
	void listReturnsEmptyArrayWhenNoFavourites() throws Exception {
		mockMvc().perform(get("/api/init/favourites").cookie(jwt(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void listReturnsOnlyOwnFavourites() throws Exception {
		repository.save(build(user.id(),      "Mine"));
		repository.save(build(otherUser.id(), "Theirs"));

		mockMvc().perform(get("/api/init/favourites").cookie(jwt(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Mine"));
	}

	@Test
	void listReturnsExpectedFields() throws Exception {
		repository.save(build(user.id(), "My Stack"));

		mockMvc().perform(get("/api/init/favourites").cookie(jwt(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("My Stack"))
				.andExpect(jsonPath("$[0].frontend").value("react"))
				.andExpect(jsonPath("$[0].frontendVersion").value("19"))
				.andExpect(jsonPath("$[0].frontendRuntime").value("node-22"))
				.andExpect(jsonPath("$[0].backend").value("spring-boot"))
				.andExpect(jsonPath("$[0].backendVersion").value("4.0"))
				.andExpect(jsonPath("$[0].backendRuntime").value("java-21"))
				.andExpect(jsonPath("$[0].pipeline").value("github-actions"))
				.andExpect(jsonPath("$[0].pipelineMaturity").value("l2"))
				.andExpect(jsonPath("$[0].includeDocker").value(true));
	}

	// ------------------------------------------------------------------
	// POST /api/init/favourites
	// ------------------------------------------------------------------

	@Test
	void saveCreatesNewFavouriteAndReturns201() throws Exception {
		MvcResult result = mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody("Spring Default")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Spring Default"))
				.andExpect(jsonPath("$.id").isString())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID savedId = UUID.fromString(body.get("id").asString());
		assertThat(repository.findByIdAndUserId(savedId, user.id())).isPresent();
	}

	@Test
	void savePersistsAllFields() throws Exception {
		MvcResult result = mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody("Full Fields")))
				.andExpect(status().isCreated())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID savedId = UUID.fromString(body.get("id").asString());
		FavouriteStack saved = repository.findByIdAndUserId(savedId, user.id()).orElseThrow();

		assertThat(saved.frontend()).isEqualTo("react");
		assertThat(saved.frontendVersion()).isEqualTo("19");
		assertThat(saved.frontendRuntime()).isEqualTo("node-22");
		assertThat(saved.backend()).isEqualTo("spring-boot");
		assertThat(saved.backendVersion()).isEqualTo("4.0");
		assertThat(saved.backendRuntime()).isEqualTo("java-21");
		assertThat(saved.pipeline()).isEqualTo("github-actions");
		assertThat(saved.pipelineMaturity()).isEqualTo("l2");
		assertThat(saved.includeDocker()).isTrue();
	}

	@Test
	void saveRejectsMissingName() throws Exception {
		mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "frontend": "react", "frontendVersion": "19", "frontendRuntime": "node-22",
								  "backend": "spring-boot", "backendVersion": "4.0", "backendRuntime": "java-21",
								  "pipeline": "github-actions", "pipelineMaturity": "l2", "includeDocker": true
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void saveRejectsMissingFrontend() throws Exception {
		mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Bad", "frontendVersion": "19", "frontendRuntime": "node-22",
								  "backend": "spring-boot", "backendVersion": "4.0", "backendRuntime": "java-21",
								  "pipeline": "github-actions", "pipelineMaturity": "l2", "includeDocker": false
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void saveRejectsNameExceeding64Characters() throws Exception {
		String longName = "a".repeat(65);
		mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody(longName)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void saveReturns422WhenUserExceedsLimit() throws Exception {
		int max = repository.maxPerUser();
		for (int i = 0; i < max; i++) {
			repository.save(build(user.id(), "Stack " + i));
		}

		mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody("One Too Many")))
				.andExpect(status().is(422));
	}

	@Test
	void saveDoesNotCountOtherUsersTowardsLimit() throws Exception {
		// Fill other user to the limit — should have no effect on our user.
		int max = repository.maxPerUser();
		for (int i = 0; i < max; i++) {
			repository.save(build(otherUser.id(), "Stack " + i));
		}

		mockMvc().perform(post("/api/init/favourites")
						.cookie(jwt(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody("Mine")))
				.andExpect(status().isCreated());
	}

	// ------------------------------------------------------------------
	// DELETE /api/init/favourites/{id}
	// ------------------------------------------------------------------

	@Test
	void deleteOwnFavouriteReturns204() throws Exception {
		FavouriteStack fav = build(user.id(), "Delete Me");
		repository.save(fav);

		mockMvc().perform(delete("/api/init/favourites/" + fav.id()).cookie(jwt(user)))
				.andExpect(status().isNoContent());

		assertThat(repository.findByIdAndUserId(fav.id(), user.id())).isEmpty();
	}

	@Test
	void deleteAnotherUsersEntryReturns404() throws Exception {
		FavouriteStack fav = build(otherUser.id(), "Not Mine");
		repository.save(fav);

		mockMvc().perform(delete("/api/init/favourites/" + fav.id()).cookie(jwt(user)))
				.andExpect(status().isNotFound());

		assertThat(repository.findByIdAndUserId(fav.id(), otherUser.id())).isPresent();
	}

	@Test
	void deleteNonexistentIdReturns404() throws Exception {
		mockMvc().perform(delete("/api/init/favourites/" + UUID.randomUUID()).cookie(jwt(user)))
				.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private String validBody(String name) {
		return """
				{
				  "name": "%s",
				  "frontend": "react",
				  "frontendVersion": "19",
				  "frontendRuntime": "node-22",
				  "backend": "spring-boot",
				  "backendVersion": "4.0",
				  "backendRuntime": "java-21",
				  "pipeline": "github-actions",
				  "pipelineMaturity": "l2",
				  "includeDocker": true
				}
				""".formatted(name);
	}

	private FavouriteStack build(UUID owner, String name) {
		return new FavouriteStack(
				UUID.randomUUID(), owner, name,
				"react", "19", "node-22",
				"spring-boot", "4.0", "java-21",
				"github-actions", "l2", true,
				OffsetDateTime.now(ZoneOffset.UTC));
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
