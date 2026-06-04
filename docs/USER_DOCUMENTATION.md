# Scaffy — User Documentation

*Instructions for use*

---

## 1. Introduction

### 1.1 Purpose of the document

This document is intended for users of the Scaffy web application. It explains how to use the application: how to sign in, how to generate a new project, how to analyze a CI/CD pipeline, and how to manage workspaces and connected repositories. It is written so that a new user — or a new team member — can understand how the application works by following it screen by screen.


### 1.2 About Scaffy

Scaffy is a web tool that supports developers with two DevOps tasks:

- **Project initializer** — generates a new project with a working CI/CD pipeline already wired up, in the spirit of `start.spring.io`. The user picks a technology stack and downloads a ready-to-run project as a ZIP archive.
- **Pipeline analyzer** — examines an existing GitHub Actions or GitLab CI pipeline, scores it against a five-level maturity model across seven dimensions, and (optionally) returns AI-generated improvement suggestions.

Signed-in users additionally work inside **workspaces**, where they can connect GitHub/GitLab accounts and repositories, analyze the live pipelines of those repositories, track how maturity changes over time, and publish generated projects straight to GitHub.

---

## 2. Accessing the application

### 2.1 Web address

Scaffy is a web application opened in a browser at the address provided by your team. For local development the frontend runs at:

```
http://localhost:5173
```

In a deployed environment the application is served behind a reverse proxy over HTTPS at your team's domain.

### 2.2 Navigation overview

The top navigation bar is available on every screen and contains:

- **Home** — the landing page.
- **Projects** — the dashboard of connected repositories (shown only when signed in).
- **Initializer** — the project generation wizard.
- **Pipeline Analyzer** — the pipeline analysis screen.
- **Design Language** — an internal design-system reference.

On the right side of the bar the user finds the sign-in options (when signed out) or the account menu (when signed in). The footer repeats the main links.

---

## 3. Signing in

The initializer and the analyzer can be used without signing in. Signing in is required for workspaces, connected repositories, generation history, favourites, applying fixes, and publishing to GitHub.

### 3.1 Sign-in methods

In the top-right corner the user clicks the sign-in control and chooses one of three identity providers:

- **Google**
- **GitHub**
- **GitLab** (GitLab.com or a registered self-hosted instance)

After choosing a provider the user is redirected to that provider's consent screen, authorizes Scaffy, and is redirected back. Scaffy then establishes the session automatically.

#### 3.1.1 Sign in with Google

The user clicks **Continue with Google**, selects a Google account, and grants access. Scaffy returns the user to the application, now signed in.

#### 3.1.2 Sign in with GitHub

The user clicks **Continue with GitHub** and authorizes the Scaffy OAuth application. GitHub sign-in is also what later allows Scaffy to read workflow files and create repositories on the user's behalf (see section 7.2).

#### 3.1.3 Sign in with GitLab

The user opens the **GitLab** menu and chooses either **GitLab.com** or a previously registered self-hosted GitLab instance (see section 7.3). The remaining steps are the same as for the other providers.

### 3.2 The account menu and signing out

Once signed in, the user's account control appears in the top-right corner. Opening it reveals:

- **Projects** — go to the dashboard.
- **Members** — manage workspace members.
- **Workspace settings** — manage the active workspace.
- **Log out** — end the session.

### 3.3 Workspaces

Work that belongs to a team — connected accounts, repositories, and analyses — lives inside a **workspace**. The active workspace name is shown on the dashboard and settings screens. A user can belong to several workspaces; creating and managing them is covered in section 7.

---

## 4. Initializing a project

The initializer is a four-step wizard that builds a new project and hands it back as a ZIP archive.

### 4.1 Opening the initializer

The user selects **Initializer** in the navigation bar. The wizard opens on **Step 1 — Project details**. The four steps are shown together; the user works through them and then generates the project.

### 4.2 Step 1 — Project details

The user enters the project's basic information:

- **Project name** (mandatory) — lowercase letters, digits, and hyphens; 2–64 characters; must start with a letter (for example `my-awesome-app`). If the name breaks these rules, the field is marked and generation is blocked until it is corrected.
- **Pipeline maturity** (mandatory) — the target maturity level for the generated pipeline.
- **Docker** — a toggle that decides whether Docker configuration is included. Some maturity presets require Docker, in which case it is enabled automatically and the wizard explains why.

### 4.3 Step 2 — Frontend

The user selects the frontend framework and its version/runtime. Supported options: **Angular**, **Vue**, **React**.

### 4.4 Step 3 — Backend

The user selects the backend framework and its version/runtime. Supported options: **Spring Boot**, **.NET**, **NestJS**.

### 4.5 Step 4 — CI/CD pipeline

The user selects the CI/CD provider for the generated pipeline: **GitHub Actions** or **GitLab CI**. The provider determines which configuration file is added to the project (`.github/workflows/…` or `.gitlab-ci.yml`).

A **Summary** panel shows the current selection (frontend, backend, pipeline, maturity, Docker). Anything not yet selected is marked as *Not selected*.

### 4.6 Generating the project

When the configuration is complete, the user clicks **Generate project**. The button shows *Generating…* while the work is in progress.

Scaffy generates the project in the background and shows:

- a **generation progress** indicator with a status message (*Queued for generation*, *Generating project*, *Artifact ready*, or *Generation failed*);
- a **Build log** with the steps performed;
- a **Technical details** section listing the job identifier, the status, and whether the download is *Ready* or *Pending*.

If generation fails, Scaffy shows the failure and does not offer a download.

### 4.7 Downloading the project

When the status becomes **Artifact ready**, the download becomes available. The user clicks the download action and the browser saves `<projectName>.zip`. After extracting the archive, the project contains the source structure, the selected CI/CD configuration, and Docker configuration when it was requested.

Generated projects are produced on demand and are not stored as permanent user data.

### 4.8 Publishing to GitHub (signed-in users)

Instead of (or in addition to) downloading, a signed-in user with a connected GitHub account can publish the generated project to a **new GitHub repository**:

1. The user chooses **Publish to GitHub** for a completed generation.
2. The user enters a **repository name** (letters, digits, dots, underscores, hyphens; up to 100 characters) and an optional **description**.
3. Scaffy creates the repository and commits the project files, showing progress until it finishes and displaying the new repository's address.

Publishing requires a GitHub connection with repository and workflow permissions (see section 7.2). If the connection is missing or lacks those permissions, Scaffy explains what to reconnect.

### 4.9 Saving a favourite stack (signed-in users)

A signed-in user can save a frequently used configuration as a **favourite** by giving it a name. Saved favourites can later be selected to pre-fill the wizard; only the project name still needs to be entered.

---

## 5. Analyzing a pipeline

The analyzer scores an existing CI/CD pipeline against a five-level maturity model.

### 5.1 Uploading a pipeline file

1. The user selects **Pipeline Analyzer** in the navigation bar.
2. In the **Pipeline file** panel the user uploads a `.yml` or `.yaml` file (GitHub Actions or GitLab CI).
3. Scaffy detects the provider, parses the file, and analyzes it. While processing, it shows *Analyzing pipeline*.

If the file is empty, has the wrong extension, is not valid YAML, or is from an unsupported provider, Scaffy shows a clear message (for example *File cannot be analyzed* or *Analysis failed*) and no report is produced.

### 5.2 The maturity report

When the analysis completes, the **Report** panel shows:

- the **Overall rating** — the overall maturity level (1–5);
- a **Report breakdown** with a score and level for each of the seven dimensions: **build, test, code analysis, security scanning, artifacts, deployment, notifications**;
- a **spider/radar graph** of the dimension scores;
- for each dimension, the practices that are **present** (positives), **weak** (smells), and **missing**.

Dimensions that cannot be evaluated are marked as *Dimension not evaluated* rather than hidden.

### 5.3 AI recommendations (optional)

From the report the user can request **AI recommendations**. Scaffy sends the pipeline and the analysis to the configured AI provider and shows prioritized suggestions with code examples. AI recommendations are optional: if no AI provider is configured or it is unavailable, Scaffy shows a clear message and keeps the base maturity report visible.

### 5.4 Applying a suggested fix (signed-in users)

For a single finding, Scaffy can propose a concrete fix, including an edit to the workflow file. For a connected repository, a signed-in user can review the proposed change, confirm a commit message, and **apply** the fix — Scaffy then commits the edited workflow file to the repository through the user's GitHub connection. Scaffy never changes a pipeline without this explicit action.

---

## 6. Projects and connected repositories

The **Projects** view (the dashboard) is where signed-in users work with their connected repositories. It is available from the navigation bar once signed in.

### 6.1 The Projects view

The view shows the active workspace name and the repositories connected to it as **project cards**. If no provider account is connected, Scaffy prompts the user to *Connect GitHub or GitLab to get started*. If no repositories are connected yet, it prompts *Add your first repository*.

### 6.2 Connecting a repository

1. The user opens the list of available GitHub or GitLab repositories.
2. The user selects a repository and connects it.
3. The repository appears as a project card in the workspace.

Connecting requires the relevant provider account to be connected first (section 7.2).

### 6.3 Analyzing a connected repository

The user opens a project card and chooses **Analyze**. Scaffy reads the repository's current workflow file directly from the provider and runs the same analysis described in section 5.2, then shows the result on the card. If no supported workflow file is found, or the provider cannot be read (for example an expired token), Scaffy reports the problem.

### 6.4 Analysis history and delta

Each analysis is saved, so a repository accumulates a **history** of runs. The user can open the **Delta** view to see how maturity changed between the two most recent runs — per dimension, marked as improved, unchanged, or regressed. When only one run exists, Scaffy indicates there is nothing to compare against yet.

### 6.5 Operational health

For a connected repository, the **Operational Health** card summarizes recent workflow run metrics (such as recent run outcomes and branch health), giving a quick read on the pipeline's day-to-day reliability.

---

## 7. Workspace administration

Workspace management is reached from the account menu: **Workspace settings** and **Members**.

### 7.1 Workspace settings

The **Settings** screen shows the active workspace and lets an authorized member:

- **Workspace name** — rename the workspace.
- **Workspace details** — review the workspace's information.
- **Invitations** — if the user has been invited to a workspace, the invitation appears here and can be accepted.

### 7.2 Provider connections

Under **Connections → Your repository accounts**, the user links the Git provider accounts that Scaffy may act on:

- **GitHub** — click **Connect GitHub** to authorize, or disconnect an existing connection.
- **GitLab.com** — click **Connect GitLab.com** to authorize, or disconnect.

A connection is what allows Scaffy to list repositories, read workflow files, apply fixes, and publish projects. Disconnecting removes Scaffy's stored access for that provider.

### 7.3 Self-hosted GitLab instances

Under **Integrations → Self-hosted GitLab**, an authorized member can register a self-hosted GitLab instance for the workspace:

1. Enter the instance **base URL**, **client ID**, **client secret**, and an optional display name.
2. Add the **redirect URI** shown by Scaffy to the GitLab OAuth application.
3. Save the instance. It then becomes selectable when signing in or connecting GitLab.

### 7.4 Members and invitations

The **Members** screen lists the people in the workspace and their roles, and lets an authorized member manage them:

- **Invite a teammate** — enter the teammate's **email** and choose a **role**; an invitation is created.
- **People** — the list of current members with a role badge; a member (other than an owner) can be removed.
- **Pending invitations** — outstanding invitations, which can be revoked.

An invited user accepts the invitation from their own Workspace settings (section 7.1) and then becomes a member.

---

## 8. Common controls and field types

Throughout the application the following controls are used:

- **Text field** — free text entry, for example the project name. Fields with invalid values are marked with an explanation.
- **Selection cards / radio groups** — choose one option from a set, for example the frontend, backend, or pipeline provider.
- **Dropdowns** — choose a version, runtime, or role.
- **Toggle** — an on/off switch, for example *Include Docker*.
- **File upload** — select a file from the computer, for example the pipeline YAML in the analyzer.
- **Primary buttons** — perform the main action of a screen, for example **Generate project**, **Analyze**, **Connect GitHub**.
- **Status rows** — show progress and outcomes (*Loading*, *Analyzing*, *Artifact ready*, error messages).

**Mandatory fields** must be completed before the related action (generating, analyzing, inviting, etc.) can be performed. Where an action depends on a prerequisite — such as a connected provider account or an active workspace — Scaffy explains what is needed before continuing.

---

## 9. Constraints and limitations

- Supported pipeline formats: **GitHub Actions** (`.github/workflows/*.yml`) and **GitLab CI** (`.gitlab-ci.yml`) only.
- The initializer supports predefined stack combinations only; it is not a generic generator for arbitrary technologies.
- AI recommendations require a configured AI provider and are always optional — they never block the maturity report.
- Generated projects are starting points, not production-ready solutions, and are not stored as permanent user data.
- Out of scope: Jenkins, CircleCI, Travis CI, and Azure DevOps; automatic pipeline changes without user approval; direct application deployment; and integration with external code-quality tools such as SonarQube.
