# Scaffy Ops

Docker Compose setup for deploying Scaffy frontend and backend together behind Traefik.

## Services

- `traefik`: public edge router on port `80`.
- `scaffy-fe`: builds the Vite React app and serves static assets internally on port `4173`.
- `scaffy-be`: builds the Spring Boot app and exposes it only inside the Compose network on port `8080`.

Traefik routes `/api/*` to `scaffy-be:8080` and all other paths to `scaffy-fe:4173`, so browser requests can use same-origin API paths such as `/api/health`.

## Deploy

```sh
cd scaffy-ops
cp .env.example .env
docker compose up -d --build
```

Override the public HTTP port in `.env` when another reverse proxy owns port `80`:

```sh
SCAFFY_HTTP_PORT=8088
```

## Check

```sh
docker compose ps
curl http://localhost:${SCAFFY_HTTP_PORT:-80}/api/health
```
