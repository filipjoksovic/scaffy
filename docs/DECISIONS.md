# Scaffy - Architecture and Project Decisions

This document records the main product, architecture, deployment, and quality decisions behind Scaffy. It complements `ARCHITECTURE.md`, which describes the current system structure, by explaining why the system is shaped this way and what tradeoffs were accepted.


## Decision 1 - Split slow work into background workers

**Decision:** Project generation and repository publishing are executed by separate Node/TypeScript workers instead of directly inside request/response API calls.

**Rationale:** Generating project archives and publishing repositories can take longer than a normal HTTP request should. They can involve framework artifacts, ZIP creation, object storage, provider APIs, and retries. Moving this work to workers keeps the API responsive and gives the UI a clean job-status model.

**Alternatives considered:** The backend could generate and publish synchronously, but that would increase request timeouts, make failures harder to recover from, and couple API responsiveness to external command and provider latency.

**Consequences:** The system needs Redis queues, job records in PostgreSQL, worker health handling, and polling/progress UI. The benefit is a more scalable and realistic architecture.

## Decision 2 - Compose generated projects from official artifacts plus a Scaffy overlay

**Decision:** Generated projects are assembled from cached framework artifacts and Scaffy-owned overlay files such as README, `.gitignore`, Docker files, and CI/CD pipeline templates.

**Rationale:** Official framework CLIs and templates produce more realistic starter projects than hand-written placeholder structures. The overlay lets Scaffy add its own consistent CI/CD and project conventions on top.

**Alternatives considered:** Generating every file manually would give complete control but would be brittle and quickly outdated. Calling official CLIs on every request would be fresh but slow and dependent on network/tool availability.

**Consequences:** Cached artifacts need to be regenerated when framework versions change. Overlay files must be tested against the cached artifacts to avoid broken generated projects.

## Decision 3 - Support GitHub Actions and GitLab CI first

**Decision:** Pipeline generation and analysis support GitHub Actions and GitLab CI as the first CI/CD providers.

**Rationale:** These providers are common, well documented, and cover both GitHub and GitLab repository ecosystems. Supporting two providers is enough to demonstrate provider detection, parsing, rule evaluation, and provider-specific template rendering without making the analyzer too broad.

**Alternatives considered:** Adding Jenkins, CircleCI, Travis CI, or Azure DevOps would increase parser and rule complexity. Those systems were left out to keep the maturity model consistent and maintainable.

**Consequences:** The analyzer must clearly reject unsupported pipeline formats and explain the limitation to users.

## Decision 4 - Use PostgreSQL, Redis, and MinIO/S3 for separate storage concerns

**Decision:** PostgreSQL is the system of record, Redis is used for queues and cache, and MinIO/S3-compatible storage is used for generated ZIP artifacts.

**Rationale:** These technologies match different data lifecycles. User accounts, workspaces, repositories, analyses, and job metadata need relational consistency. Queues and repeated-analysis cache need fast transient storage. Generated ZIP files are binary artifacts and fit object storage better than database rows.

**Alternatives considered:** Storing everything in PostgreSQL would simplify deployment, but would make binary artifacts and queue semantics less appropriate. Using only filesystem storage would be simpler locally but weaker for deployment and workers.

**Consequences:** Local and production deployments need multiple infrastructure services. The separation makes the architecture clearer and closer to a real production system.

## Decision 5 - Make AI recommendations optional and user-approved

**Decision:** LLM-backed recommendations and per-finding fixes are optional. The base maturity report works without an AI provider, and Scaffy never commits a generated fix without explicit user confirmation.

**Rationale:** The deterministic analyzer should remain useful and testable on its own. AI adds value for explanation and suggested edits, but it can fail, be unavailable, or produce suggestions that require review.

**Alternatives considered:** AI could be required for every analysis, or fixes could be applied automatically. Both alternatives would make the system less reliable and less safe.

**Consequences:** The UI must show clear fallback states when AI is unavailable. The backend must keep a review/apply step between generated suggestions and repository changes.

## Decision 6 - Use OAuth provider connections and workspaces

**Decision:** Signed-in users work inside workspaces and connect GitHub/GitLab accounts for repository access.

**Rationale:** Repository analysis, history, publishing, and fix application are team-oriented workflows. Workspaces create a natural boundary for connected repositories, provider tokens, members, roles, and invitations.

**Alternatives considered:** A single-user model would be simpler, but would not demonstrate team collaboration or repository ownership boundaries. Global provider connections would be risky because access should be scoped to a user and workspace context.

**Consequences:** The backend needs authentication, authorization, token encryption, workspace membership checks, and provider-specific integration handling. The UI needs account, workspace, and connection management screens.

## Decision 7 - Deploy with Docker Compose, Traefik, and HTTPS

**Decision:** The backend stack is deployable on a VPS with Docker Compose and Traefik. The frontend can be served by the Compose stack or separately as a static Vite application, with the deployed API available over HTTPS.

**Rationale:** Docker Compose is understandable and practical for a student project while still representing a real deployment topology. Traefik provides reverse proxying and TLS termination. Keeping the backend API behind a domain satisfies the requirement that the project must not rely only on localhost.

**Alternatives considered:** Kubernetes would be more scalable but too heavy for the project scope. A fully managed platform would simplify operations but hide too much of the deployment architecture.

**Consequences:** Deployment documentation must cover domains, environment variables, OAuth callback URLs, secrets, and health checks. The root README should point to the live frontend and API once those URLs are finalized.

## Decision 8 - Keep services private behind the reverse proxy

**Decision:** Application containers and infrastructure services are not exposed publicly unless Traefik needs to route to them.

**Rationale:** PostgreSQL, Redis, MinIO, workers, and internal backend ports should not be directly reachable from the public internet. Traefik centralizes public routing and TLS.

**Alternatives considered:** Exposing service ports directly would make debugging easier, but would weaken the deployment security model.

**Consequences:** Health checks, Compose networking, and routing labels need to be configured correctly. Debugging production issues usually happens through logs, health endpoints, or SSH access rather than public service ports.

## Decision 9 - Use automated tests, CI, and SonarCloud coverage

**Decision:** The project uses automated backend/frontend tests and GitHub Actions workflows for builds, tests, deployment, and SonarCloud analysis.

**Rationale:** Pipeline parsing, maturity scoring, recommendation logic, authentication, repository integration, and UI API clients are risk-heavy areas. Automated tests give evidence that core behavior is repeatable and help protect against regressions.

**Alternatives considered:** Manual testing only would be faster initially, but weak for a project that itself focuses on CI/CD maturity and quality practices.

**Consequences:** The repository includes build workflows, coverage configuration, and tests across backend and frontend areas. New features should include focused tests, especially when they change analysis behavior, repository writes, authentication, or generated artifacts.

## Decision 10 - Document user, API, architecture, deployment, and decisions separately

**Decision:** Documentation is split by audience: user manual, use cases, architecture, diagrams, API collection, operations README, and this decisions document.

**Rationale:** The project requirements ask for implementation, documentation, diagrams, and explanation of architecture decisions. Separate documents keep each topic readable and make it easier for evaluators to find the relevant evidence.

**Alternatives considered:** A single large README would be easier to maintain, but would become too long and would hide important project artifacts.

**Consequences:** The root README acts as an index. Documentation should be kept in sync when architecture, deployment, or supported workflows change.

