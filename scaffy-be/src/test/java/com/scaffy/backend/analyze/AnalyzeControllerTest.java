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
				.andExpect(jsonPath("$.dimensions[0].dimension").value("build"))
				.andExpect(jsonPath("$.dimensions[0].score").value(0.85))
				.andExpect(jsonPath("$.dimensions[0].level").value(5))
				.andExpect(jsonPath("$.dimensions[0].status").value("complete"))
				.andExpect(jsonPath("$.dimensions[0].confidence").value("high"))
				.andExpect(jsonPath("$.dimensions[0].detectedPractices[0].evidence").value("npm run build"));
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
