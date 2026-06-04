# Scaffy — Architecture

This document describes the responsibilities of each component in the system. The boxes in `architecture-diagram.drawio` are named only; this is where you read what each one does. For how data moves between them see `network-diagram.drawio`.

## Overview

Scaffy is a monorepo of five deployable parts plus shared infrastructure. The backend handles fast request/response work and hands anything slow — generating a project, publishing a repository — to background workers over a Redis queue. The workers report progress back through PostgreSQL so the UI can poll it.

## Services

### scaffy-fe — React + Vite SPA
The web client. Hosts the landing page, the four-step initializer wizard, the pipeline analyzer, the projects dashboard, and the workspace screens. It talks to the backend over REST; in production its requests are proxied by Traefik.

### scaffy-be — Spring Boot REST API (Java 25)
The core API. Owns all synchronous logic and the persistence layer, and enqueues slow work for the workers. It is organized into six modules:

- **init** — the project initializer: validates a stack selection against the catalog, serves the catalog, runs the synchronous ZIP generation, and creates/queues asynchronous generation jobs (plus favourites and history).
- **analyze** — the pipeline maturity engine: detects the provider, parses GitHub Actions / GitLab CI YAML, runs the capability rule sets, and scores each of the seven dimensions to an overall maturity level.
- **recommend** — the LLM-backed suggestion engine: builds prompts from an analysis, calls the AI provider for prioritized recommendations and per-finding fixes, and applies an approved fix as a commit.
- **auth** — authentication and provider access: OAuth login (Google / GitHub / GitLab incl. self-hosted instances), JWT cookie sessions, and encrypted storage of provider tokens.
- **workspace** — multi-tenant teams: workspaces, members, roles, invitations, and workspace-scoped GitLab instances.
- **repository** — connected repositories: connecting GitHub/GitLab repos, analyzing their live workflow files, storing run history and deltas, workflow metrics, and queuing GitHub publications.

### scaffy-generator — Node / TypeScript worker
Consumes initializer jobs from the Redis queue, builds the scaffold (framework artifacts + Scaffy overlay) into a ZIP, stores the ZIP in object storage, and updates the job status in PostgreSQL. Has no inbound HTTP port.

### scaffy-publisher — Node / TypeScript worker
Consumes publication jobs from the Redis queue, reads the generated ZIP from object storage, and creates a new GitHub repository, committing the project files using the user's connected GitHub token. Has no inbound HTTP port.

### scaffy-ops — Docker Compose + Traefik
The deployment stack. Traefik is the reverse proxy and TLS terminator: it routes `/api`, `/oauth2`, and `/login` to scaffy-be and everything else to scaffy-fe. Compose wires together all services and the infrastructure on one bridge network.

## Data stores

- **PostgreSQL** — system of record: analyses and runs, generation and publication jobs, workspaces and members, provider connections and tokens.
- **Redis** — the job queues for the generator and publisher, plus a cache for repeated pipeline analyses.
- **MinIO / S3** — object storage for generated project artifacts (the ZIPs the generator produces and the publisher reads).

## External services

- **LLM Provider** (Claude / OpenAI-compatible) — generates the AI recommendations and fixes.
- **GitHub API** — OAuth sign-in, reading workflow files, creating repositories, and committing.
- **GitLab API** — OAuth sign-in and reading workflow files (GitLab.com and self-hosted instances).
- **Google** — OAuth sign-in only.

## Key flows (at a glance)

- **Generate a project:** scaffy-be enqueues a job in Redis → scaffy-generator builds the ZIP, stores it in MinIO, updates PostgreSQL → scaffy-be streams the artifact to the user.
- **Analyze a pipeline:** scaffy-be (analyze) parses and scores the YAML, optionally caching the result in Redis and persisting runs in PostgreSQL.
- **Publish to GitHub:** scaffy-be enqueues a publication job → scaffy-publisher reads the ZIP from MinIO and pushes it to GitHub.
