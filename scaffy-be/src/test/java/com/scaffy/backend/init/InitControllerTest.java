package com.scaffy.backend.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class InitControllerTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void returnsZipForValidAngularSpringBootRequest() throws Exception {
		String body = """
				{
					"projectName": "demo-app",
					"frontend": "angular",
					"backend": "spring-boot",
					"pipeline": "github-actions",
					"includeDocker": true
				}
				""";

		MvcResult result = mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.parseMediaType("application/zip")))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-app.zip\""))
				.andReturn();

		Map<String, byte[]> entries = readZip(result.getResponse().getContentAsByteArray());

		// Overlay (Scaffy-owned).
		assertThat(entries).containsKeys(
				"demo-app/README.md",
				"demo-app/.gitignore",
				"demo-app/.github/workflows/ci.yml");

		// Spring Boot artifact, unpacked under backend/, with tokens expanded.
		assertThat(entries).containsKeys(
				"demo-app/backend/pom.xml",
				"demo-app/backend/mvnw",
				"demo-app/backend/mvnw.cmd",
				"demo-app/backend/src/main/resources/application.properties",
				"demo-app/backend/src/main/java/com/example/demoapp/DemoAppApplication.java",
				"demo-app/backend/src/test/java/com/example/demoapp/DemoAppApplicationTests.java");

		// Angular artifact, unpacked under frontend/.
		assertThat(entries).containsKeys(
				"demo-app/frontend/package.json",
				"demo-app/frontend/angular.json",
				"demo-app/frontend/src/main.ts",
				"demo-app/frontend/src/app/app.component.ts",
				"demo-app/frontend/public/favicon.ico");

		// No GitLab CI when GitHub Actions is requested.
		assertThat(entries).doesNotContainKey("demo-app/.gitlab-ci.yml");

		// Token expansion happened in file contents, not just paths.
		String applicationJava = new String(
				entries.get("demo-app/backend/src/main/java/com/example/demoapp/DemoAppApplication.java"),
				StandardCharsets.UTF_8);
		assertThat(applicationJava)
				.contains("package com.example.demoapp;")
				.contains("public class DemoAppApplication")
				.doesNotContain("__SCAFFY_");

		String packageJson = new String(
				entries.get("demo-app/frontend/package.json"),
				StandardCharsets.UTF_8);
		assertThat(packageJson)
				.contains("\"name\": \"demo-app\"")
				.doesNotContain("__SCAFFY_");

		// Docker support files.
		assertThat(entries).containsKeys(
				"demo-app/backend/Dockerfile",
				"demo-app/frontend/Dockerfile",
				"demo-app/docker-compose.yml");

		String backendDockerfile = new String(entries.get("demo-app/backend/Dockerfile"), StandardCharsets.UTF_8);
		assertThat(backendDockerfile)
				.contains("EXPOSE 8080")
				.contains("eclipse-temurin:25");

		String frontendDockerfile = new String(entries.get("demo-app/frontend/Dockerfile"), StandardCharsets.UTF_8);
		assertThat(frontendDockerfile)
				.contains("dist/demo-app/browser")
				.contains("EXPOSE 80")
				.doesNotContain("{{projectName}}");

		String dockerCompose = new String(entries.get("demo-app/docker-compose.yml"), StandardCharsets.UTF_8);
		assertThat(dockerCompose)
				.contains("8080:8080")
				.contains("80:80");
	}

	@Test
	void omitsDockerArtifactsWhenNotRequested() throws Exception {
		String body = """
				{
					"projectName": "lean-app",
					"frontend": "angular",
					"backend": "spring-boot",
					"pipeline": "github-actions"
				}
				""";

		MvcResult result = mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn();

		Map<String, byte[]> entries = readZip(result.getResponse().getContentAsByteArray());
		assertThat(entries).doesNotContainKeys(
				"lean-app/backend/Dockerfile",
				"lean-app/frontend/Dockerfile",
				"lean-app/docker-compose.yml");
	}

	@Test
	void selectsGitlabCiWhenRequested() throws Exception {
		String body = """
				{
					"projectName": "another-app",
					"frontend": "angular",
					"backend": "spring-boot",
					"pipeline": "gitlab-ci"
				}
				""";

		MvcResult result = mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn();

		Map<String, byte[]> entries = readZip(result.getResponse().getContentAsByteArray());
		assertThat(entries).containsKey("another-app/.gitlab-ci.yml");
		assertThat(entries).doesNotContainKey("another-app/.github/workflows/ci.yml");
	}

	@Test
	void rejectsUnsupportedFrontend() throws Exception {
		String body = """
				{
					"projectName": "demo-app",
					"frontend": "vue",
					"backend": "spring-boot",
					"pipeline": "github-actions"
				}
				""";

		mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Unsupported stack combination"))
				.andExpect(jsonPath("$.message").value(
						"Frontend 'vue' is not supported in iteration 1."));
	}

	@Test
	void rejectsMissingProjectName() throws Exception {
		String body = """
				{
					"frontend": "angular",
					"backend": "spring-boot",
					"pipeline": "github-actions"
				}
				""";

		mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Invalid request"));
	}

	@Test
	void rejectsMalformedProjectName() throws Exception {
		String body = """
				{
					"projectName": "Demo App!",
					"frontend": "angular",
					"backend": "spring-boot",
					"pipeline": "github-actions"
				}
				""";

		mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Invalid request"));
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		mockMvc().perform(post("/api/init")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Malformed JSON"));
	}

	private Map<String, byte[]> readZip(byte[] zipBytes) throws Exception {
		Map<String, byte[]> entries = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				entries.put(entry.getName(), in.readAllBytes());
			}
		}
		return entries;
	}
}
