# Scaffy — Use Case Specifications

This document describes the use cases shown in `use-case-diagram.drawio`, step by step. Each use case lists its actors, preconditions, a numbered **main flow**, and the **alternative / exception flows** that branch from it. For the screen-by-screen end-user manual see `USER_DOCUMENTATION.md`; for component responsibilities see `ARCHITECTURE.md`.

## How to read the flows

- Main-flow steps are numbered `1, 2, 3, …`.
- An alternative or exception branches from a specific step. The first branch at step 2 is `2.1`, the second is `2.2`, and its steps continue as `2.1.1, 2.1.2, …`.
- Each branch ends by either **resuming** the main flow at a named step or **ending** the use case.

So `2.1.2` reads as *"second step of the first alternative that branches from main-flow step 2."*

## Actors

| Actor | Type | Role |
| --- | --- | --- |
| **User** | Human | Anyone using Scaffy. All users have the same capabilities. |
| **Git Provider** | External system | GitHub / GitLab — reads workflow files, creates repositories, commits (supporting). |
| **LLM Provider** | External system | Generates AI recommendations and fixes (supporting). |
| **API Client** | External system | A script or service that calls the REST API directly. |

## Relationships between use cases

| Relationship | Meaning |
| --- | --- |
| UC-1 Initialize project **«include»** UC-6 Generate CI/CD config | Generating a project always produces the CI/CD configuration. |
| UC-1 Initialize project **«include»** UC-7 Download project ZIP | Generating a project always ends with a downloadable archive. |
| UC-8 Publish to GitHub **«extend»** UC-1 Initialize project | Publishing is an optional alternative to downloading. |
| UC-2 Analyze pipeline **«include»** UC-9 View maturity report | Analysis always produces a report. |
| UC-10 View AI recommendation **«extend»** UC-9 View maturity report | AI suggestions optionally extend the report. |
| UC-11 Apply suggested fix **«extend»** UC-10 View AI recommendation | Applying a fix optionally extends a recommendation. |

---

# Primary use cases

## UC-1 — Initialize project

**Actors:** User
**Preconditions:** The initializer is open and the stack catalog has loaded.
**Trigger:** The user wants a new project with a CI/CD pipeline already wired up.

**Main flow**

1. The user enters the project name and selects the pipeline-maturity preset and the Docker option.
2. The user selects the frontend framework and its version/runtime.
3. The user selects the backend framework and its version/runtime.
4. The user selects the CI/CD provider (GitHub Actions or GitLab CI).
5. The user confirms the configuration.
6. Scaffy validates the selection against the catalog and queues a generation job.
7. Scaffy generates the project, including the CI/CD configuration (**«include» UC-6**).
8. Scaffy makes the project available for download as a ZIP archive (**«include» UC-7**).

**Alternative / exception flows**

- **1.1 — Invalid project name** *(branch at step 1)*
  - 1.1.1 Scaffy marks the field and explains the naming rule (lowercase, digits, hyphens; 2–64 chars; starts with a letter).
  - 1.1.2 The user corrects the name; flow resumes at step 5.
- **6.1 — Unsupported stack combination** *(branch at step 6)*
  - 6.1.1 Scaffy rejects the request with a structured error naming the unsupported field.
  - 6.1.2 The user changes the selection; flow resumes at step 5.
- **7.1 — Generation fails** *(branch at step 7)*
  - 7.1.1 Scaffy records the failure on the job and shows it as failed.
  - 7.1.2 No archive is offered. The use case ends.
- **8.1 — Publish instead of download** *(branch at step 8)*
  - 8.1.1 The user chooses to publish the project to GitHub (**«extend» UC-8**) rather than download it.

**Postcondition:** A generated starter project is available to the user (as a download or, optionally, a GitHub repository).

---

## UC-2 — Analyze pipeline

**Actors:** User
**Preconditions:** The analyzer is open; the user has a GitHub Actions or GitLab CI YAML file.
**Trigger:** The user wants to assess the maturity of an existing pipeline.

**Main flow**

1. The user uploads a `.yml` / `.yaml` pipeline file.
2. Scaffy detects the provider (GitHub Actions or GitLab CI) and parses the YAML.
3. Scaffy evaluates the pipeline across the seven maturity dimensions.
4. Scaffy computes a score and a maturity level (1–5) per dimension and overall.
5. Scaffy presents the maturity report (**«include» UC-9**).

**Alternative / exception flows**

- **1.1 — Empty or non-YAML file** *(branch at step 1)*
  - 1.1.1 Scaffy rejects files that are empty or not `.yml`/`.yaml` with a clear message; flow resumes at step 1.
- **2.1 — Invalid YAML** *(branch at step 2)*
  - 2.1.1 Scaffy returns a validation error describing the parse problem. The use case ends.
- **2.2 — Unsupported provider** *(branch at step 2)*
  - 2.2.1 Scaffy reports that only GitHub Actions and GitLab CI are supported. The use case ends.
- **3.1 — Recently analyzed pipeline** *(branch at step 3)*
  - 3.1.1 If the same pipeline was analyzed recently, Scaffy returns a cached result; flow resumes at step 5.

**Postcondition:** A maturity report exists for the submitted pipeline.

---

## UC-3 — Connect repository

**Actors:** User · Git Provider *(supporting)*
**Preconditions:** The user is signed in with an active workspace and a connected Git provider account.
**Trigger:** The user wants to analyze the live pipeline of one of their repositories.

**Main flow**

1. The user opens the list of available GitHub or GitLab repositories.
2. Scaffy fetches the repositories from the Git Provider.
3. The user selects a repository (and instance, for self-hosted GitLab) and confirms.
4. Scaffy stores the repository connection in the workspace.
5. The repository appears as a project in the workspace.

**Alternative / exception flows**

- **1.1 — Provider not connected** *(branch at step 1)*
  - 1.1.1 Scaffy prompts the user to connect the Git provider account first.
  - 1.1.2 After connecting, flow resumes at step 1.
- **2.1 — Provider request fails** *(branch at step 2)*
  - 2.1.1 Scaffy reports the provider error (for example an expired token) and suggests reconnecting. The use case ends.

**Postcondition:** The repository is connected and can be analyzed.

---

## UC-4 — Track analysis history

**Actors:** User
**Preconditions:** The user is signed in; a connected repository has at least one analysis run.
**Trigger:** The user wants to see how a repository's maturity is changing over time.

**Main flow**

1. The user opens the analysis section of a connected repository.
2. Scaffy shows the latest result and the list of past runs.
3. The user opens the delta view.
4. Scaffy shows how the maturity changed between the two most recent runs, per dimension (improved / unchanged / regressed).

**Alternative / exception flows**

- **3.1 — Only one run exists** *(branch at step 3)*
  - 3.1.1 Scaffy indicates there is no previous run to compare against; flow resumes at step 2.

**Postcondition:** The user understands the repository's maturity trend.

---

## UC-5 — Manage workspace

**Actors:** User
**Preconditions:** The user is signed in and is a member of the workspace.
**Trigger:** The user wants to create a workspace or manage its members and settings.

**Main flow**

1. The user opens the workspace settings or members screen.
2. Scaffy shows the workspace details, members, and pending invitations.
3. The user performs an action: create or rename a workspace, invite a member by email and role, accept an invitation, remove a member, or revoke an invitation.
4. Scaffy applies the change and refreshes the view.

**Alternative / exception flows**

- **3.1 — Caller not authorized** *(branch at step 3)*
  - 3.1.1 Scaffy denies the action based on the caller's role; the workspace is unchanged.
- **3.2 — Invitation token invalid or expired** *(branch at step 3)*
  - 3.2.1 Scaffy reports that the invitation is no longer valid. The use case ends.

**Postcondition:** The workspace's membership and settings reflect the change.

---

# Included and extending use cases

## UC-6 — Generate CI/CD config  ·  «include» of UC-1

**Actors:** User *(via UC-1)*

**Main flow**

1. Scaffy identifies the selected stack and CI/CD provider.
2. Scaffy selects the matching pipeline template.
3. Scaffy renders the pipeline configuration (`.github/workflows/…` for GitHub Actions, `.gitlab-ci.yml` for GitLab CI).
4. Scaffy adds the configuration to the generated project.

**Postcondition:** The generated project contains a CI/CD pipeline for the chosen provider.

---

## UC-7 — Download project ZIP  ·  «include» of UC-1

**Actors:** User *(via UC-1)*
**Preconditions:** Generation has succeeded.

**Main flow**

1. Scaffy packages the generated project as a ZIP archive.
2. The user clicks the download action.
3. The browser saves `<projectName>.zip`.
4. The user extracts the archive and opens the project.

**Alternative / exception flows**

- **2.1 — Job not ready** *(branch at step 2)*
  - 2.1.1 If the job has not succeeded, Scaffy returns a "not ready" response and offers no archive; the user waits and retries at step 2.

**Postcondition:** The user holds the generated project as a ZIP. Scaffy does not store generated scaffolds permanently.

---

## UC-8 — Publish to GitHub  ·  «extend» of UC-1

**Actors:** User · Git Provider *(supporting)*
**Preconditions:** A generation job has succeeded; the user is signed in with a GitHub connection that has repository and workflow permissions.

**Main flow**

1. The user chooses to publish the generated project and enters a repository name and optional description.
2. Scaffy queues a publication job.
3. Scaffy creates a new GitHub repository via the Git Provider and commits the project files.
4. Scaffy shows the new repository's address.

**Alternative / exception flows**

- **1.1 — Invalid repository name** *(branch at step 1)*
  - 1.1.1 Scaffy rejects names outside the allowed set or longer than 100 characters; flow resumes at step 1.
- **3.1 — GitHub not connected or missing scopes** *(branch at step 3)*
  - 3.1.1 Scaffy fails the job and asks the user to connect/reconnect GitHub with repository and workflow access. The use case ends.
- **3.2 — Repository creation or commit fails** *(branch at step 3)*
  - 3.2.1 Scaffy records the provider error on the job and shows it as failed. The use case ends.

**Postcondition:** The generated project exists in a new GitHub repository.

---

## UC-9 — View maturity report  ·  «include» of UC-2

**Actors:** User *(via UC-2)*
**Preconditions:** An analysis has completed.

**Main flow**

1. Scaffy displays the overall maturity level.
2. Scaffy shows the score and level for each of the seven dimensions.
3. Scaffy distinguishes practices that are present, weak (smells), and missing.
4. The user reviews the report to decide what to improve next.

**Alternative / exception flows**

- **2.1 — A dimension cannot be evaluated** *(branch at step 2)*
  - 2.1.1 Scaffy marks that dimension as not evaluated rather than scoring it; flow resumes at step 3.

**Postcondition:** The user understands the pipeline's strengths and gaps.

---

## UC-10 — View AI recommendation  ·  «extend» of UC-9

**Actors:** User · LLM Provider *(supporting)*
**Preconditions:** A maturity report exists; an LLM provider is configured.

**Main flow**

1. The user requests AI recommendations from the report.
2. Scaffy builds the prompt from the pipeline and the analysis result.
3. Scaffy calls the LLM Provider.
4. The LLM Provider returns prioritized suggestions with code examples.
5. Scaffy displays the recommendations next to the report.

**Alternative / exception flows**

- **3.1 — No provider configured** *(branch at step 3)*
  - 3.1.1 Scaffy informs the user that AI recommendations require a valid LLM subscription and keeps showing the base report. The use case ends.
- **3.2 — Provider unavailable** *(branch at step 3)*
  - 3.2.1 Scaffy shows a clear error and keeps the base report visible. The use case ends.

**Postcondition:** The user has optional, prioritized improvement guidance. AI never blocks the base report.

---

## UC-11 — Apply suggested fix  ·  «extend» of UC-10

**Actors:** User · Git Provider *(supporting)*
**Preconditions:** A fix proposal exists for a finding; the user is signed in with an active workspace and a connected GitHub repository.

**Main flow**

1. The user reviews a proposed fix and chooses to apply it.
2. The user confirms a commit message.
3. Scaffy resolves the active workspace and the user's GitHub connection.
4. Scaffy commits the edited workflow file to the repository via the Git Provider.
5. Scaffy confirms the commit.

**Alternative / exception flows**

- **3.1 — No active workspace or connection** *(branch at step 3)*
  - 3.1.1 Scaffy reports that a workspace and GitHub connection are required; the user connects and the flow resumes at step 1.
- **4.1 — Commit rejected by the provider** *(branch at step 4)*
  - 4.1.1 Scaffy surfaces the provider error and makes no partial change. The use case ends.

**Postcondition:** The approved fix is committed to the repository. Scaffy never changes a pipeline without this explicit action.

---

# Programmatic access

## UC-12 — Access REST API

**Actors:** API Client
**Preconditions:** The backend is reachable.
**Trigger:** An external system wants to initialize a project or analyze a pipeline without the web UI.

**Main flow**

1. The API client sends an HTTP request to a Scaffy endpoint.
2. Scaffy validates the request.
3. For initialization, the client calls `POST /api/init`; Scaffy generates a project and returns the ZIP.
4. For analysis, the client uploads a YAML file to `POST /api/analyze`; Scaffy returns the maturity report as JSON.

**Alternative / exception flows**

- **2.1 — Invalid request body** *(branch at step 2)*
  - 2.1.1 Scaffy returns a structured `400` error (`Invalid request` or `Malformed JSON`). The use case ends.
- **2.2 — Unsupported stack combination** *(branch at step 2)*
  - 2.2.1 Scaffy returns a `400` error (`Unsupported stack combination`). The use case ends.
- **2.3 — Invalid or unsupported pipeline** *(branch at step 2)*
  - 2.3.1 Scaffy returns an analysis error describing the problem. The use case ends.

**Postcondition:** The external system receives a generated project, a maturity report, or a clear structured error.
