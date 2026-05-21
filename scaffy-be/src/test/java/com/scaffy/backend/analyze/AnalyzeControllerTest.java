package com.scaffy.backend.analyze;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AnalyzeControllerTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void acceptsMultipartYamlUpload() throws Exception {
		MockMultipartFile file = yaml("ci.yml", """
				name: CI
				on: [push]
				jobs:
				  frontend:
				    runs-on: ubuntu-latest
				    steps:
				      - run: npm ci
				      - run: npm run build
				""");

		mockMvc().perform(multipart("/api/analyze").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("github-actions"))
				.andExpect(jsonPath("$.overallScore").value(0.03))
				.andExpect(jsonPath("$.overallLevel").value(1))
				.andExpect(jsonPath("$.overallStatus").value("partial"))
				.andExpect(jsonPath("$.dimensions[0].dimension").value("build_release"))
				.andExpect(jsonPath("$.dimensions[0].score").value(0.15))
				.andExpect(jsonPath("$.dimensions[0].level").value(2))
				.andExpect(jsonPath("$.dimensions[0].status").value("partial"))
				.andExpect(jsonPath("$.dimensions[1].dimension").value("testing_maturity"))
				.andExpect(jsonPath("$.dimensions[1].score").value(0.0))
				.andExpect(jsonPath("$.dimensions[1].status").value("missing"))
				.andExpect(jsonPath("$.dimensions[2].dimension").value("workflow_quality"))
				.andExpect(jsonPath("$.dimensions[2].score").value(0.0))
				.andExpect(jsonPath("$.dimensions[2].status").value("missing"))
				.andExpect(jsonPath("$.dimensions[3].dimension").value("security_integration"))
				.andExpect(jsonPath("$.dimensions[3].score").value(0.0))
				.andExpect(jsonPath("$.dimensions[3].status").value("missing"))
				.andExpect(jsonPath("$.dimensions[4].dimension").value("deployment_automation"))
				.andExpect(jsonPath("$.dimensions[4].score").value(0.0))
				.andExpect(jsonPath("$.dimensions[4].status").value("missing"));
	}

	@Test
	void rejectsMissingFile() throws Exception {
		mockMvc().perform(multipart("/api/analyze"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Invalid pipeline upload"));
	}

	@Test
	void rejectsUnsupportedExtension() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"ci.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"name: CI".getBytes(StandardCharsets.UTF_8));

		mockMvc().perform(multipart("/api/analyze").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Unsupported file type"));
	}

	@Test
	void rejectsUnsupportedProvider() throws Exception {
		MockMultipartFile file = yaml("compose.yml", """
				services:
				  app:
				    image: nginx
				""");

		mockMvc().perform(multipart("/api/analyze").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Unsupported pipeline provider"));
	}

	private MockMultipartFile yaml(String filename, String content) {
		return new MockMultipartFile(
				"file",
				filename,
				"application/x-yaml",
				content.getBytes(StandardCharsets.UTF_8));
	}
}
