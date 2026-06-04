# Scaffy

Scaffy is a web tool that helps developers with two DevOps tasks:

1. **Initialize** a new project that already ships with a working CI/CD pipeline, in the spirit of `start.spring.io`.
2. **Analyze** an existing GitHub Actions or GitLab CI pipeline against a five-level maturity model and surface concrete, AI-assisted improvements.

Signed-in users work inside **workspaces** where they can connect GitHub/GitLab accounts and repositories, analyze the live pipelines of those repositories, track how maturity changes over time, and publish generated projects straight to GitHub.

This repository is the monorepo: frontend, backend, two background workers, the ops stack, and the documentation.

## App

- **Local development:** the frontend runs at `http://localhost:5173` (see [Run locally](#run-locally)).
- **Deployed:** served behind Traefik over HTTPS at your team's domain — _add the deployed URL here_.

## Documentation

| Document | What's in it |
| --- | --- |
| [docs/USER_DOCUMENTATION.md](docs/USER_DOCUMENTATION.md) | End-user manual — how to use every screen, step by step. |
| [docs/USE_CASES.md](docs/USE_CASES.md) | Use-case specifications — main flows and alternative/exception flows. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | What each service and module is responsible for. |
| [docs/architecture-diagram.drawio](docs/architecture-diagram.drawio) | Components and how they connect. |
| [docs/network-diagram.drawio](docs/network-diagram.drawio) | Network boundaries, ports, and data flow. |
| [docs/use-case-diagram.drawio](docs/use-case-diagram.drawio) | Actors and use cases (UML). |
| [docs/scaffy.postman_collection.json](docs/scaffy.postman_collection.json) | Importable Postman collection for the full REST API. |

Open the `.drawio` files at [app.diagrams.net](https://app.diagrams.net) or with the draw.io editor in VS Code.

## Features at a glance

- **Project initializer** — a four-step wizard: pick a frontend (Angular, Vue, React), a backend (Spring Boot, .NET, NestJS), a CI/CD provider (GitHub Actions or GitLab CI), a maturity preset, and Docker; then download the project as a ZIP. Generation runs asynchronously through a queue and a worker.
- **Pipeline analyzer** — upload a GitHub Actions or GitLab CI YAML and get a maturity report scored 1–5 across seven dimensions (build, test, code analysis, security scanning, artifacts, deployment, notifications).
- **AI recommendations** — optional, LLM-backed suggestions and per-finding fixes that can be committed back to a connected repository.
- **Accounts & workspaces** — OAuth sign-in (Google, GitHub, GitLab incl. self-hosted), connected repositories, run history and deltas, and team workspaces with members and invitations.
- **REST API** — every capability is available programmatically; see the Postman collection.

## Repository layout

| Directory | Contents |
| --- | --- |
| [scaffy-fe/](scaffy-fe/) | React + Vite frontend — initializer wizard, analyzer, dashboard, workspace screens. |
| [scaffy-be/](scaffy-be/) | Spring Boot REST API (Java 25) — init, analyze, recommend, auth, workspace, repository. |
| [scaffy-generator/](scaffy-generator/) | Node/TypeScript worker — builds the scaffold ZIP from queued generation jobs. |
| [scaffy-publisher/](scaffy-publisher/) | Node/TypeScript worker — publishes generated projects to GitHub. |
| [scaffy-ops/](scaffy-ops/) | Docker Compose stack with Traefik, PostgreSQL, Redis, and MinIO. |
| [docs/](docs/) | Documentation and diagrams. |

## Tech stack

React + Vite · Spring Boot (Java 25) · Node/TypeScript workers · PostgreSQL · Redis · MinIO (S3) · Traefik · Docker Compose.

## Run locally

For day-to-day development, run Postgres, Redis, MinIO, and the initializer generator in Docker, then run the apps from source:

```sh
cd scaffy-ops
cp .env.local.example .env.local
docker compose --env-file .env.local -f compose.local.yml up -d
```

In a second terminal, start the backend with the local profile:

```sh
cd scaffy-be
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

In a third terminal, start the frontend:

```sh
cd scaffy-fe
npm install
npm run dev
```

Open `http://localhost:5173`. The frontend dev config points API calls at `http://localhost:8080`, and the backend `local` profile uses non-secure `SameSite=Lax` auth cookies for plain localhost.

To test OAuth locally, add provider credentials to your backend environment or IDE run config and register these callback URLs:

```txt
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

The full Compose stack also exists for production-like runs; it builds every service and routes them behind Traefik:

```sh
cd scaffy-ops
cp .env.example .env
docker compose up -d --build
```

Then `curl http://localhost/api/health` should return `{"status":"ok",...}`. See [scaffy-ops/README.md](scaffy-ops/README.md) for port overrides, and the README inside each subdirectory to run just one service.

## REST API

The API is served under `/api` (e.g. `http://localhost:8080/api` locally, or `http://localhost/api` via the Compose stack). The two headline endpoints are:

- `POST /api/init` — generate a project scaffold and return it as a ZIP.
- `POST /api/analyze` — analyze a pipeline YAML and return a maturity report.

Import [docs/scaffy.postman_collection.json](docs/scaffy.postman_collection.json) for the full surface (initializer jobs, recommendations, auth, repositories, workspaces) with example requests. Set the collection's `baseUrl` variable to your environment.

## For contributors

The initializer composes each project from two sources that converge into one ZIP: cached **framework artifacts** produced by the official CLIs (Spring Initializr, `ng new`, …) and a **Scaffy overlay** (root README, `.gitignore`, CI workflows). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the component breakdown and `scaffy-be`'s `init` package for the wiring.

Cached artifacts under `scaffy-be/src/main/resources/artifacts/` are regenerated by `scaffy-be/scripts/regenerate-artifacts.sh` — run it when bumping a framework version, when an upstream CLI changes its defaults, or when adding a stack:

```sh
bash scaffy-be/scripts/regenerate-artifacts.sh          # regenerate everything (with build validation)
SKIP_VALIDATE=1 bash scaffy-be/scripts/regenerate-artifacts.sh   # faster, skip validation
STACK=angular bash scaffy-be/scripts/regenerate-artifacts.sh     # one stack only
```

### CI & coverage

`.github/workflows/sonarcloud.yml` runs backend (Maven + JaCoCo) and frontend (Vitest) tests and uploads coverage to a single SonarCloud project. It requires a `SONAR_TOKEN` secret plus `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION` variables. If SonarCloud automatic analysis is enabled, turn it off so CI-based coverage is imported.

## Team

| Role | Name |
| --- | --- |
| Member | Filip Joksović |
| Member | Jernej Jerot |
| Member | Georgi Dimov |
| Member | Andrej Delimanchev |
