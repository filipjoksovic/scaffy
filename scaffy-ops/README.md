# Scaffy Ops

Docker Compose setup for deploying Scaffy frontend and backend together behind Traefik on a VPS.

## Local development

Use the local Compose file when you want PostgreSQL, Redis, MinIO, and the initializer generator from Docker while running Spring Boot/Vite directly from source:

```sh
cd scaffy-ops
cp .env.local.example .env.local
docker compose --env-file .env.local -f compose.local.yml up -d
```

Then start the backend from the repository root or `scaffy-be/` with the local Spring profile:

```sh
cd ../scaffy-be
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Start the frontend separately:

```sh
cd ../scaffy-fe
npm install
npm run dev
```

Local URLs:

```txt
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Health:   http://localhost:8080/api/health
Postgres: localhost:5432/scaffy
Redis:    localhost:6379
MinIO:    http://localhost:9001
```

For local OAuth apps, configure callback URLs:

```txt
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
http://localhost:8080/login/oauth2/code/gitlab
```

Set `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `GITHUB_OAUTH_CLIENT_ID`, and `GITHUB_OAUTH_CLIENT_SECRET` (optionally `GITLAB_OAUTH_CLIENT_ID` / `GITLAB_OAUTH_CLIENT_SECRET` for gitlab.com) in your shell or IDE run configuration before starting the backend. The `local` Spring profile uses `SCAFFY_AUTH_COOKIE_SECURE=false` and `SCAFFY_AUTH_COOKIE_SAME_SITE=Lax`, which is appropriate for plain `http://localhost` development.

## Services

- `traefik`: public edge router on ports `80` and `443`, with Let's Encrypt TLS.
- `scaffy-fe`: builds the Vite React app and serves static assets internally on port `4173`.
- `scaffy-be`: builds the Spring Boot app and exposes it only inside the Compose network on port `8080`.
- `scaffy-generator`: consumes initializer jobs from Redis, runs the stack CLIs, and uploads ZIP artifacts to MinIO/S3-compatible storage.
- `redis`: queue used by async initializer generation.
- `minio`: local S3-compatible artifact storage for generated ZIP files.

Traefik routes `https://$SCAFFY_DOMAIN/api/*` to `scaffy-be:8080` and all other paths to `scaffy-fe:4173`, so browser requests can use same-origin API paths such as `/api/health`.

## Hetzner VPS deploy

1. Point a DNS `A` record for your domain or subdomain to the Hetzner VPS public IPv4 address.
2. Install Docker Engine and the Compose plugin on the VPS.
3. Open inbound ports `80` and `443` in the VPS firewall.
4. Copy or clone this repository onto the VPS.
5. Configure the deployment environment:

```sh
cd scaffy-ops
cp .env.example .env
```

Edit `.env`:

```env
SCAFFY_DOMAIN=api.scaffy.fijol.io
LETSENCRYPT_EMAIL=admin@example.com
SCAFFY_CORS_ALLOWED_ORIGINS=https://scaffy.fijol.io
SCAFFY_APP_FRONTEND_URL=https://scaffy.fijol.io
SCAFFY_JWT_SECRET=<at-least-32-random-bytes>
POSTGRES_PASSWORD=<strong-database-password>
SCAFFY_HTTP_PORT=80
SCAFFY_HTTPS_PORT=443
```

Start the stack:

```sh
docker compose up -d --build
```

Check it:

```sh
docker compose ps
curl -fsS https://$SCAFFY_DOMAIN/api/health
```

## GitHub Actions deploy

The workflow at `.github/workflows/deploy-vps.yml` deploys the backend API stack automatically on pushes to `main` when backend or ops files change.

It does not sync the repository source to the VPS. The workflow builds the backend Docker image in GitHub Actions, pushes it to GitHub Container Registry, copies only `compose.api.yml` and the generated `.env` file to the VPS, then runs `docker compose pull` and restarts `traefik` plus `scaffy-be`.

Add these repository secrets in GitHub under `Settings -> Secrets and variables -> Actions -> Secrets`:

```txt
HETZNER_HOST=<your-vps-ip-or-hostname>
HETZNER_USER=<ssh-user>
HETZNER_SSH_KEY=<private-ssh-key>
LETSENCRYPT_EMAIL=<your-email>
POSTGRES_PASSWORD=<strong-database-password>
MINIO_ROOT_PASSWORD=<strong-object-storage-password>
SCAFFY_JWT_SECRET=<at-least-32-random-bytes>
GOOGLE_OAUTH_CLIENT_ID=<google-oauth-client-id>
GOOGLE_OAUTH_CLIENT_SECRET=<google-oauth-client-secret>
OAUTH_GITHUB_CLIENT_ID=<github-oauth-client-id>
OAUTH_GITHUB_CLIENT_SECRET=<github-oauth-client-secret>
```

Add these repository variables under `Settings -> Secrets and variables -> Actions -> Variables`:

```txt
SCAFFY_DOMAIN=api.scaffy.fijol.io
SCAFFY_CORS_ALLOWED_ORIGINS=https://scaffy.fijol.io
SCAFFY_APP_FRONTEND_URL=https://scaffy.fijol.io
```

Optional variables:

```txt
DEPLOY_PATH=/opt/scaffy
HETZNER_PORT=22
SCAFFY_HTTP_PORT=80
SCAFFY_HTTPS_PORT=443
POSTGRES_DB=scaffy
POSTGRES_USER=scaffy
MINIO_ROOT_USER=scaffy
```

`MINIO_ROOT_USER` can also be stored as a secret if you do not want the object storage username in repository variables.

## OAuth callbacks

Configure OAuth applications with backend callback URLs:

```txt
https://api.scaffy.fijol.io/login/oauth2/code/google
https://api.scaffy.fijol.io/login/oauth2/code/github
https://api.scaffy.fijol.io/login/oauth2/code/github-repos
https://api.scaffy.fijol.io/login/oauth2/code/gitlab
```

For local development, use:

```txt
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
http://localhost:8080/login/oauth2/code/github-repos
http://localhost:8080/login/oauth2/code/gitlab
```

The GitHub login (`github`) and repository connect (`github-repos`) flows share the same GitHub OAuth App. A GitHub OAuth App only allows a single Authorization callback URL, but matching is by path prefix, so set its callback to the common parent to cover both registrations:

```txt
https://api.scaffy.fijol.io/login/oauth2/code
```

Google should request `openid profile email`. The GitHub login flow (`github`) requests identity scopes only: `read:user` and `user:email`. The repository connect flow (`github-repos`) additionally requests `repo` and `workflow`.

### GitLab (gitlab.com and self-hosted)

Setting `GITLAB_OAUTH_CLIENT_ID` / `GITLAB_OAUTH_CLIENT_SECRET` enables a built-in **gitlab.com** login at registration id `gitlab` (callback `.../login/oauth2/code/gitlab`).

**Self-hosted instances are added by users at runtime** — no env config. A user opens the login menu, chooses "Add GitLab instance", and enters the instance URL plus a client id/secret from an OAuth application they registered on that instance. Scaffy derives a stable registration id `gitlab-<host>`; the corresponding callback URL the user must register on their instance is shown after submitting (e.g. `https://api.scaffy.fijol.io/login/oauth2/code/gitlab-gitlab-example-com`). GitLab OAuth apps must be granted the `read_user read_api read_repository` scopes.

The VPS user must be able to run Docker Compose in `DEPLOY_PATH`. If the user is not `root`, add it to the `docker` group or configure passwordless Docker access for deployments.

The workflow uses GitHub's built-in `GITHUB_TOKEN` to publish and pull the GHCR image, so no extra registry token is needed unless you later move images to a separate private registry.

## React on Vercel?

For this project, keeping React in this Compose stack is the simplest production setup because the frontend already calls the backend with same-origin paths like `/api/init`. That avoids CORS, extra environment variables, and cross-domain cookie/header issues.

Deploying React to Vercel is also valid if you want their preview deployments and CDN workflow. In that setup, keep `scaffy-be` on the VPS and publish only the Vite app to Vercel.

Use separate domains:

```txt
scaffy.fijol.io -> Vercel frontend
api.scaffy.fijol.io -> Hetzner VPS backend
```

Set this Vercel environment variable on the frontend project:

```env
VITE_API_BASE_URL=https://api.scaffy.fijol.io
```

Set this variable in `scaffy-ops/.env` on the VPS so Spring Boot allows the Vercel frontend origin:

```env
SCAFFY_CORS_ALLOWED_ORIGINS=https://scaffy.fijol.io
```

For Vercel preview deployments, add the preview URL too:

```env
SCAFFY_CORS_ALLOWED_ORIGINS=https://scaffy.fijol.io,https://scaffy-fe-git-main-your-team.vercel.app
```
