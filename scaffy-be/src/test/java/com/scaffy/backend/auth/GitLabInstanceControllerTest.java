package com.scaffy.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class GitLabInstanceControllerTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	void addsInstanceAndReturnsCallbackWithoutLeakingSecret() throws Exception {
		String body = """
				{"baseUrl":"https://gitlab.controller-add.test/","clientId":"app-id","clientSecret":"app-secret"}
				""";

		mockMvc().perform(post("/api/auth/gitlab/instances")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.instance.registrationId").value("gitlab-gitlab-controller-add-test"))
				.andExpect(jsonPath("$.instance.host").value("gitlab.controller-add.test"))
				.andExpect(jsonPath("$.loginPath").value("/oauth2/authorization/gitlab-gitlab-controller-add-test"))
				.andExpect(jsonPath("$.callbackUrl")
						.value(containsString("/login/oauth2/code/gitlab-gitlab-controller-add-test")))
				.andExpect(content().string(not(containsString("app-secret"))));
	}

	@Test
	void rejectsInvalidBaseUrl() throws Exception {
		String body = """
				{"baseUrl":"not-a-url","clientId":"app-id","clientSecret":"app-secret"}
				""";

		mockMvc().perform(post("/api/auth/gitlab/instances")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("baseUrl")));
	}

	@Test
	void rejectsMissingClientSecret() throws Exception {
		String body = """
				{"baseUrl":"https://gitlab.missing-secret.test","clientId":"app-id"}
				""";

		mockMvc().perform(post("/api/auth/gitlab/instances")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("clientSecret")));
	}

	@Test
	void listsInstancesWithoutSecrets() throws Exception {
		String body = """
				{"baseUrl":"https://gitlab.controller-list.test","clientId":"app-id","clientSecret":"app-secret"}
				""";
		mockMvc().perform(post("/api/auth/gitlab/instances")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk());

		mockMvc().perform(get("/api/auth/gitlab/instances"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("app-secret"))))
				.andExpect(content().string(containsString("gitlab-gitlab-controller-list-test")));
	}
}
