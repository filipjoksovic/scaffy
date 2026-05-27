package com.scaffy.backend.analyze;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AnalyzeSamplePipelinesIntegrationTest {

private static final Path SAMPLE_DIR = Path.of("src/test/resources/analyze-samples");

@Autowired
private WebApplicationContext context;

private MockMvc mockMvc() {
return MockMvcBuilders.webAppContextSetup(context).build();
}

@Test
void samplePipelineFilesCanBeUploadedThroughAnalyzeEndpoint() throws Exception {
for (Sample sample : samples()) {
mockMvc().perform(multipart("/api/analyze").file(file(sample.filename())))
.andExpect(status().isOk())
.andExpect(jsonPath("$.provider").value(sample.provider()))
.andExpect(jsonPath("$.dimensions[0].dimension").value("build_release"))
.andExpect(jsonPath("$.dimensions[1].dimension").value("testing_maturity"))
.andExpect(jsonPath("$.dimensions[2].dimension").value("workflow_quality"))
.andExpect(jsonPath("$.dimensions[3].dimension").value("security_integration"))
.andExpect(jsonPath("$.dimensions[4].dimension").value("deployment_automation"));
}
}

private List<Sample> samples() {
return List.of(
new Sample("github-01-test-only-missing.yml", "github-actions"),
new Sample("github-02-manual-build-low.yml", "github-actions"),
new Sample("github-03-node-build-no-artifact.yml", "github-actions"),
new Sample("github-04-node-build-with-artifact.yml", "github-actions"),
new Sample("gitlab-05-docker-build-no-explicit-deps.yml", "gitlab-ci"),
new Sample("gitlab-06-manual-java-build.yml", "gitlab-ci"),
new Sample("gitlab-07-java-build-complete.yml", "gitlab-ci"),
new Sample("gitlab-08-dotnet-build-complete.yml", "gitlab-ci"),
new Sample("test-01-build-only-test-missing.yml", "github-actions"),
new Sample("test-02-manual-test-weak.yml", "github-actions"),
new Sample("test-03-automated-test-partial.yml", "gitlab-ci"),
new Sample("test-04-test-with-artifact-strong.yml", "github-actions"),
new Sample("test-05-complete-test-suite.yml", "gitlab-ci"),
new Sample("deploy-01-build-test-only-missing.yml", "github-actions"),
new Sample("deploy-02-manual-deploy-partial.yml", "github-actions"),
new Sample("deploy-03-auto-deploy-no-validation.yml", "gitlab-ci"),
new Sample("deploy-04-complete-kubernetes.yml", "github-actions"),
new Sample("deploy-05-cloud-provider.yml", "gitlab-ci"),
new Sample("quality-01-build-only-missing.yml", "github-actions"),
new Sample("quality-02-github-lint-partial.yml", "github-actions"),
new Sample("quality-03-github-typescript-complete.yml", "github-actions"),
new Sample("quality-04-gitlab-java-python.yml", "gitlab-ci"),
new Sample("quality-05-sonar-super-linter.yml", "github-actions"),
new Sample("artifact-01-missing.yml", "github-actions"),
new Sample("artifact-02-github-upload-partial.yml", "github-actions"),
new Sample("artifact-03-gitlab-paths-partial.yml", "gitlab-ci"),
new Sample("artifact-04-docker-image-complete.yml", "github-actions"),
new Sample("artifact-05-package-publish-complete.yml", "github-actions"),
new Sample("notification-01-missing.yml", "github-actions"),
new Sample("notification-02-github-slack-failure-complete.yml", "github-actions"),
new Sample("notification-03-gitlab-teams-on-failure.yml", "gitlab-ci"),
new Sample("notification-04-discord-webhook-partial.yml", "github-actions"),
new Sample("notification-05-email-notification.yml", "github-actions"),
new Sample("security-01-missing.yml", "github-actions"),
new Sample("security-02-github-codeql-complete.yml", "github-actions"),
new Sample("security-03-dependency-scan-partial.yml", "github-actions"),
new Sample("security-04-gitlab-security-reports-complete.yml", "gitlab-ci"),
new Sample("security-05-container-iac-secret-scan.yml", "github-actions"),
new Sample("workflow-01-missing-permissions.yml", "github-actions"),
new Sample("workflow-02-unpinned-actions.yml", "github-actions"),
new Sample("workflow-03-timeout-missing.yml", "github-actions"),
new Sample("workflow-04-concurrency-present.yml", "github-actions"),
new Sample("workflow-05-path-filters.yml", "github-actions"),
new Sample("workflow-06-hardcoded-secret.yml", "github-actions"),
new Sample("workflow-07-policy-as-code.yml", "github-actions"),
new Sample("workflow-08-rollback-signal.yml", "gitlab-ci"),
new Sample("workflow-09-default-job-names.yml", "github-actions"),
new Sample("workflow-10-matrix-cache-use.yml", "github-actions"));
}

private MockMultipartFile file(String filename) throws IOException {
return new MockMultipartFile(
"file",
filename,
"application/x-yaml",
Files.readAllBytes(SAMPLE_DIR.resolve(filename)));
}

private record Sample(String filename, String provider) {
}
}