# Scaffy User Documentation: Use Case Scenarios

This document defines the user scenarios for the Scaffy use cases presented in the use case diagram (`docs/dpu.drawio`) and described in the project vision document.

Scaffy is a web-based tool intended to support users in the following activities:

- initialization of a new software project with Docker and CI/CD configuration,
- analysis of an existing CI/CD pipeline,
- review of a structured maturity report,
- review of optional AI-based improvement recommendations,
- programmatic access to the same functionality through a REST API.

## Actors

| Actor | Description |
| --- | --- |
| User | General actor representing a person using the Scaffy web application. |
| Developer | Specialized user who initializes new projects or improves existing CI/CD pipelines. |
| DevOps | Specialized user responsible for pipeline quality, maturity, automation, and delivery practices. |
| Student | Specialized user who uses Scaffy to learn DevOps and CI/CD concepts. |
| API Client | External software system or script that accesses Scaffy through REST endpoints. |
| LLM Provider | External AI service used to generate contextual improvement recommendations. |

## UC-01: Initialize Project

**User story:** As a developer, I would like to initialize a new project with a selected technology stack so that I can begin development with an established project structure and basic DevOps configuration.

**Primary actors:** Developer, Student

**Preconditions:**

- The user has opened the Scaffy web application.
- The initializer is available.
- The selected stack combination is supported by Scaffy.

**Main scenario:**

1. The user opens the project initializer.
2. Scaffy displays a configuration wizard for creating a new project.
3. The user selects the frontend technology, such as Angular, Vue, or React.
4. The user selects the backend technology, such as Spring Boot, .NET, or NestJS.
5. The user enters project metadata, such as project name, package name, and basic configuration.
6. The user selects the CI/CD provider, such as GitHub Actions or GitLab CI.
7. The user confirms the selected configuration.
8. Scaffy generates the scaffold project structure.
9. Scaffy includes Docker and CI/CD configuration in the generated project.
10. Scaffy prepares the project for download as a ZIP archive.

**Alternative flows:**

- If the selected stack combination is not supported, Scaffy informs the user and asks them to choose a supported combination.
- If required project metadata is missing, Scaffy marks the missing fields and prevents generation until the data is completed.

**Postcondition:**

The user receives a generated starter project that can be used as a starting point for development.

**Acceptance criteria:**

- The user can select a supported frontend stack.
- The user can select a supported backend stack.
- The user can select GitHub Actions or GitLab CI as the CI/CD provider.
- The generated project contains source structure, Docker configuration, and CI/CD configuration.

## UC-02: Generate CI/CD Configuration

**User story:** As a developer, I would like Scaffy to generate CI/CD configuration for the selected technology stack so that the initial pipeline does not need to be created manually.

**Primary actors:** Developer, Student

**Preconditions:**

- The user has configured a project in the initializer.
- The user has selected a supported CI/CD provider.

**Main scenario:**

1. The user selects the desired CI/CD provider during project initialization.
2. Scaffy identifies the selected technology stack.
3. Scaffy selects the appropriate CI/CD template.
4. Scaffy generates a pipeline configuration for the selected provider.
5. Scaffy adds the generated configuration to the scaffold project.

**Alternative flows:**

- If GitHub Actions is selected, Scaffy generates a workflow under `.github/workflows/`.
- If GitLab CI is selected, Scaffy generates a `.gitlab-ci.yml` file.

**Postcondition:**

The generated project contains a CI/CD pipeline configuration suitable for the selected stack and provider.

**Acceptance criteria:**

- The CI/CD configuration is generated automatically.
- The generated pipeline matches the selected provider.
- The pipeline includes basic build and test steps where applicable.

## UC-03: Download Generated Project

**User story:** As a user, I would like to download the generated project as a ZIP archive so that I can use it locally or import it into a version control repository.

**Primary actors:** Developer, Student

**Preconditions:**

- The user has completed the project initialization flow.
- Scaffy has successfully generated the scaffold project.

**Main scenario:**

1. Scaffy finishes generating the project files.
2. Scaffy packages the generated project as a ZIP archive.
3. The user clicks the download action.
4. The browser downloads the ZIP archive.
5. The user extracts the project and opens it in a development environment.

**Alternative flows:**

- If project generation fails, Scaffy displays an error and does not offer a broken ZIP archive.
- If the download fails in the browser, the user can request the ZIP again without re-entering the full configuration.

**Postcondition:**

The user receives a ZIP archive containing the generated scaffold project.

**Acceptance criteria:**

- The ZIP archive contains the generated source structure.
- The ZIP archive contains Docker configuration.
- The ZIP archive contains the selected CI/CD configuration.
- Scaffy does not permanently store generated scaffold ZIP files.

## UC-04: Analyze CI/CD Pipeline

**User story:** As a DevOps user, I would like to analyze an existing CI/CD pipeline so that I can determine its maturity level and identify missing DevOps practices.

**Primary actors:** Developer, DevOps, Student

**Preconditions:**

- The user has a GitHub Actions or GitLab CI YAML pipeline.
- The analyzer is available.

**Main scenario:**

1. The user opens the pipeline analyzer.
2. The user enters or pastes a CI/CD YAML configuration.
3. Scaffy detects whether the pipeline is GitHub Actions or GitLab CI.
4. Scaffy parses the YAML structure.
5. Scaffy evaluates the pipeline across the maturity dimensions.
6. Scaffy calculates a maturity level from 1 to 5.
7. Scaffy generates a structured analysis result.
8. Scaffy stores the analysis result for future access.
9. Scaffy caches repeated analysis results for a limited time when the same pipeline is analyzed again.

**Maturity dimensions:**

- Build
- Test
- Code analysis
- Security scanning
- Artifacts
- Deployment
- Notifications

**Alternative flows:**

- If the YAML is invalid, Scaffy displays a validation error.
- If the pipeline provider is unsupported, Scaffy informs the user that only GitHub Actions and GitLab CI are supported.
- If the same pipeline was recently analyzed, Scaffy may return a cached result.

**Postcondition:**

The user receives a maturity analysis for the submitted CI/CD pipeline.

**Acceptance criteria:**

- The analyzer accepts GitHub Actions YAML.
- The analyzer accepts GitLab CI YAML.
- The analyzer rejects unsupported or invalid pipeline formats with a clear message.
- The analyzer returns scores for all defined maturity dimensions.
- The analyzer returns an overall maturity level.

## UC-05: View Maturity Report

**User story:** As a user, I would like to view a structured maturity report so that I can understand the strengths and weaknesses of the submitted CI/CD pipeline.

**Primary actors:** Developer, DevOps, Student

**Preconditions:**

- The user has submitted a pipeline for analysis.
- Scaffy has completed the pipeline analysis.

**Main scenario:**

1. Scaffy displays the analysis result after the pipeline is processed.
2. The user sees the overall maturity level.
3. The user sees the score for each maturity dimension.
4. The user reviews which DevOps practices are present in the pipeline.
5. The user reviews which DevOps practices are missing or incomplete.
6. The user uses the report to decide which pipeline improvements should be made next.

**Alternative flows:**

- If analysis fails, Scaffy displays an error instead of a report.
- If some dimensions cannot be evaluated, Scaffy marks them clearly instead of hiding them.

**Postcondition:**

The user understands the pipeline maturity level and receives a structured overview of each evaluated dimension.

**Acceptance criteria:**

- The report includes an overall maturity level.
- The report includes per-dimension maturity scores.
- The report is clear enough for educational and practical DevOps use.
- The report can be displayed in the web interface.

## UC-06: View AI Recommendation

**User story:** As a user, I would like to view AI-based improvement recommendations so that I can receive concrete suggestions for improving the submitted CI/CD pipeline.

**Primary actors:** Developer, DevOps

**Supporting actor:** LLM Provider

**Preconditions:**

- The user has submitted a pipeline for analysis.
- A maturity report exists.
- AI analysis is enabled.
- A valid LLM API key or subscription is configured.

**Main scenario:**

1. The user requests AI recommendations from the maturity report.
2. Scaffy prepares the pipeline context and maturity results.
3. Scaffy sends the relevant context to the configured LLM provider.
4. The LLM provider returns contextual improvement suggestions.
5. Scaffy displays the suggestions to the user.
6. The user reviews the recommendations and related code examples.
7. The user decides which recommendations to apply manually.

**Alternative flows:**

- If the LLM provider is unavailable, Scaffy displays a clear error.
- If no API key is configured, Scaffy informs the user that AI recommendations require a valid LLM provider subscription.
- If the AI response is incomplete, Scaffy still shows the base maturity report.

**Postcondition:**

The user receives prioritized improvement recommendations with practical guidance.

**Acceptance criteria:**

- AI recommendations are optional and do not block the basic maturity report.
- Recommendations are based on the submitted pipeline and maturity results.
- Recommendations are shown as suggestions, not automatic pipeline changes.
- Scaffy does not automatically modify the user's pipeline.

## UC-07: Access REST API

**User story:** As an API client, I would like to access Scaffy through REST endpoints so that external systems can initialize projects and analyze pipelines programmatically.

**Primary actor:** API Client

**Preconditions:**

- The Scaffy backend service is running.
- The API client can send HTTP requests to the Scaffy REST API.

**Main scenario:**

1. The API client sends a request to Scaffy.
2. Scaffy validates the request.
3. For project initialization, the API client calls `/api/init`.
4. Scaffy generates a scaffold project and returns the generated archive or download response.
5. For pipeline analysis, the API client calls `/api/analyze`.
6. Scaffy analyzes the submitted YAML pipeline.
7. Scaffy returns the maturity report in a structured response.

**Alternative flows:**

- If the request body is invalid, Scaffy returns a validation error.
- If the requested stack combination is unsupported, Scaffy returns an appropriate error.
- If the submitted YAML is invalid, Scaffy returns an analysis error.
- If AI recommendations are requested but unavailable, Scaffy returns the base report without AI suggestions.

**Postcondition:**

External systems can use Scaffy functionality without using the web interface.

**Acceptance criteria:**

- `/api/init` exposes project initialization.
- `/api/analyze` exposes pipeline analysis.
- API responses are structured and suitable for integration.
- Invalid requests receive clear error responses.

## Scenario Consistency Notes

- Project generation and pipeline analysis are separate main flows.
- `Generate CI/CD configuration` and `Download generated project` are included in the project initialization flow.
- `View maturity report` is included in the pipeline analysis flow.
- `View AI recommendation` is optional and extends the pipeline analysis/reporting flow.
- `Access REST API` provides programmatic access to project initialization and pipeline analysis.
- GitHub Actions and GitLab CI are the supported pipeline providers.
- AI recommendations require an external LLM provider and are not required for the basic analyzer to work.
