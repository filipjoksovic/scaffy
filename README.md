# Scaffy

Web tool that helps developers with two DevOps pain points:

1. **Initialize** a new project with a working CI/CD pipeline already wired up (à la `start.spring.io`).
2. **Analyze** an existing GitHub Actions / GitLab CI pipeline against a 5-stage maturity model and surface concrete improvements.

This repository hosts the monorepo: backend, frontend, ops, and docs.

## Layout

| Directory                  | Contents                                                        |
| -------------------------- | --------------------------------------------------------------- |
| [scaffy-be/](scaffy-be/)   | Spring Boot REST API (Java 17) — generator + analyzer endpoints |
| [scaffy-fe/](scaffy-fe/)   | React + Vite frontend — initializer wizard and analyzer UI      |
| [scaffy-ops/](scaffy-ops/) | Docker Compose stack with Traefik routing the two services      |
| [docs/](docs/)             | Architecture diagrams (drawio + PNG)                            |

## Run locally

The simplest path is the Compose stack — it builds both services and routes them behind Traefik on port `80`:

```sh
cd scaffy-ops
cp .env.example .env
docker compose up -d --build
```

Then `curl http://localhost/api/health` should return `{"status":"ok",...}`. See [scaffy-ops/README.md](scaffy-ops/README.md) for port overrides.

To run just one service for development, see the README inside that subdirectory.

## Iteration status

Implementation is split across five one-week iterations. Current state:

- **Iteration 1 — in progress.** Project skeleton, CI for the tool itself, and the initializer's REST API for Spring Boot + Angular scaffolds.

Later iterations add the rest of the stacks (Vue, React, .NET, NestJS), the YAML analyzer, the maturity-model scoring, the LLM-backed suggestion engine, and reference projects. The full plan lives in `docs/Scaffy_Vizija - Popravljena.pdf`.

## API — `/api/init` (iteration 1)

Generates a project scaffold and returns it as a ZIP archive.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/init` (or `http://localhost/api/init` via the Compose stack)
- **Request**: `application/json`
- **Response (success)**: `200 OK`, `application/zip`, `Content-Disposition: attachment; filename="<projectName>.zip"`
- **Response (failure)**: `400 Bad Request`, `application/json`

### Supported values (iteration 1)

| Field         | Allowed values                                                           |
| ------------- | ------------------------------------------------------------------------ |
| `frontend`    | `angular`                                                                |
| `backend`     | `spring-boot`                                                            |
| `pipeline`    | `github-actions`, `gitlab-ci`                                            |
| `projectName` | lowercase letters, digits, hyphens; 2–64 chars; must start with a letter |

Anything outside this set is rejected with a structured JSON error — see "Errors" below.

### Example — successful request

```json
POST /api/init
Content-Type: application/json

{
  "projectName": "demo-app",
  "frontend": "angular",
  "backend": "spring-boot",
  "pipeline": "github-actions"
}
```

The response body is a ZIP. The `backend/` and `frontend/` trees are exactly what `start.spring.io` (Spring Boot 4.0.6) and `ng new` (Angular 18) produce, with all framework defaults intact. Scaffy adds the root README, `.gitignore`, and the CI workflow on top.

```
demo-app/
├── README.md                                 # Scaffy overlay
├── .gitignore                                # Scaffy overlay
├── .github/workflows/ci.yml                  # if pipeline = github-actions
├── .gitlab-ci.yml                            # if pipeline = gitlab-ci
├── backend/                                  # Spring Boot 4.0.6, from start.spring.io
│   ├── pom.xml, mvnw, mvnw.cmd, .mvn/
│   ├── HELP.md, .gitignore, .gitattributes
│   └── src/{main,test}/java/com/example/demoapp/...
└── frontend/                                 # Angular 18, from `ng new`
    ├── package.json, angular.json, tsconfig*.json
    ├── .editorconfig, .gitignore, .vscode/
    ├── public/favicon.ico
    └── src/{index.html, main.ts, styles.css, app/...}
```

### Errors

All error responses share the same shape:

```json
{
  "error": "<short category>",
  "message": "<human-readable detail>"
}
```

Categories:

| Trigger                                                        | `error` value                   |
| -------------------------------------------------------------- | ------------------------------- |
| Body fails bean-validation (missing/invalid field)             | `Invalid request`               |
| Body is not parseable JSON                                     | `Malformed JSON`                |
| `frontend`, `backend`, or `pipeline` outside the supported set | `Unsupported stack combination` |

Example for `"frontend": "vue"`:

```json
{
  "error": "Unsupported stack combination",
  "message": "Frontend 'vue' is not supported in iteration 1."
}
```

### Try it

PowerShell (writes the ZIP to disk):

```powershell
$body = '{"projectName":"demo-app","frontend":"angular","backend":"spring-boot","pipeline":"github-actions"}'
Invoke-WebRequest -Uri http://localhost:8080/api/init `
  -Method POST -ContentType 'application/json' -Body $body `
  -OutFile demo-app.zip
```

curl:

```sh
curl -X POST http://localhost:8080/api/init \
  -H 'Content-Type: application/json' \
  -d '{"projectName":"demo-app","frontend":"angular","backend":"spring-boot","pipeline":"github-actions"}' \
  -o demo-app.zip
```

Unzip the archive, then in `backend/` run `mvn spring-boot:run` and in `frontend/` run `npm install && npm start` to bring the two services up locally.

## How the generator is wired

For contributors touching the backend, the request flow has two parallel sources that converge into one ZIP:

```
InitController
  └─ StackValidator              (catalog membership check)
  └─ ProjectGenerator
       ├─ ArtifactComposer       — unpacks framework artifacts, expands __SCAFFY_*__ tokens
       │     ├─ artifacts/spring-boot.zip   (cached output of Spring Initializr)
       │     └─ artifacts/angular.zip       (cached output of `ng new`)
       ├─ TemplateOverlay        — renders Scaffy-owned glue, expands {{var}}
       │     └─ templates/       (root README, .gitignore, CI workflows)
       └─ ZipBuilder             — writes the merged stream into the response
```

The split is deliberate: framework artifacts are produced by the _official_ CLIs against placeholder names and committed to the repo, so the conventions and file inventory match what Angular/Spring teams ship. The Scaffy overlay is content we author (CI workflows that know about both stacks, a project README that ties them together).

### Adding a new framework stack

1. Register the stack in [`StackCatalog`](scaffy-be/src/main/java/com/scaffy/backend/init/StackCatalog.java).
2. Extend [`scaffy-be/scripts/regenerate-artifacts.sh`](scaffy-be/scripts/regenerate-artifacts.sh) with a function that runs the framework's CLI against placeholder names and post-processes the output to introduce the `__SCAFFY_*__` tokens.
3. Run the script (see below) to commit the new artifact ZIP.
4. Add a one-line `composeBackend`/`composeFrontend` branch in [`ProjectGenerator`](scaffy-be/src/main/java/com/scaffy/backend/init/generator/ProjectGenerator.java) pointing at the new artifact.

### Regenerating cached artifacts

Cached artifacts under [scaffy-be/src/main/resources/artifacts/](scaffy-be/src/main/resources/artifacts/) are produced by [scaffy-be/scripts/regenerate-artifacts.sh](scaffy-be/scripts/regenerate-artifacts.sh). Run this when you bump a framework version, when an upstream CLI changes its defaults, or when adding a new stack.

```sh
# regenerate everything, with a build-validation pass against each artifact
bash scaffy-be/scripts/regenerate-artifacts.sh

# faster — skip the npm/mvn validation step
SKIP_VALIDATE=1 bash scaffy-be/scripts/regenerate-artifacts.sh

# rebuild only one stack
STACK=angular bash scaffy-be/scripts/regenerate-artifacts.sh
```

Requires bash, GNU sed (Git Bash on Windows ships it), Node + npx, curl, python3, and a working JDK on PATH. Commit the resulting `*.zip` files alongside any code changes that depend on them.

### Token + variable maps

Two substitution schemes coexist; the choice is content-driven, not stylistic:

| Scheme                                                                                                    | Used for                | Why                                                                                                |
| --------------------------------------------------------------------------------------------------------- | ----------------------- | -------------------------------------------------------------------------------------------------- |
| `__SCAFFY_PROJECT_NAME__`, `__SCAFFY_PROJECT_PASCAL__`, `__SCAFFY_PACKAGE__`, `__SCAFFY_PACKAGE_DIR__`, … | CLI-generated artifacts | Avoids collision with Angular's own `{{ }}` interpolation that appears in real component templates |
| `{{projectName}}` (and any future `{{var}}`)                                                              | Scaffy-owned overlay    | Already in place, content authored by us so no clash                                               |

## Team

| Role   | Name               |
| ------ | ------------------ |
| Member | Filip Joksović     |
| Member | Jernej Jerot       |
| Member | Georgi Dimov       |
| Member | Andrej Delimanchev |
