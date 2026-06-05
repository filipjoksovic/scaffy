import { execa } from 'execa'
import { mkdirp, pathExists, readJson, writeJson } from 'fs-extra/esm'
import { writeFile } from 'node:fs/promises'
import path from 'node:path'
import type { CommandLogLine, CommandSpec, InitJobRequest, InitSelection } from './types.js'

const CLI_TIMEOUT_MS = 180000

export function buildCommandPlan(workspace: string, request: InitJobRequest, selection: InitSelection): CommandSpec[] {
  return [
    ...frontendCommands(workspace, selection.frontend),
    ...backendCommands(workspace, selection.backend),
  ]
}

export async function runCommand(
  spec: CommandSpec,
  onLog?: (line: CommandLogLine) => Promise<void>,
): Promise<void> {
  try {
    const subprocess = execa(spec.executable, spec.args, {
      cwd: spec.cwd,
      env: spec.env,
      timeout: spec.timeoutMs,
      stdout: 'pipe',
      stderr: 'pipe',
    })
    pipeCommandOutput(subprocess.stdout, 'stdout', onLog)
    pipeCommandOutput(subprocess.stderr, 'stderr', onLog)
    await subprocess
  } catch (error) {
    throw commandFailure(spec, error)
  }
}

function pipeCommandOutput(
  stream: NodeJS.ReadableStream | null,
  name: 'stdout' | 'stderr',
  onLog?: (line: CommandLogLine) => Promise<void>,
): void {
  if (!stream || !onLog) return
  let buffer = ''
  stream.setEncoding('utf8')
  stream.on('data', (chunk: string) => {
    buffer += chunk
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const trimmed = line.trimEnd()
      if (trimmed.length > 0) {
        onLog({ stream: name, message: trimmed }).catch(() => undefined)
      }
    }
  })
  stream.on('end', () => {
    const trimmed = buffer.trimEnd()
    if (trimmed.length > 0) {
      onLog({ stream: name, message: trimmed }).catch(() => undefined)
    }
  })
}

export async function postProcessProject(
  workspace: string,
  request: InitJobRequest,
  selection: InitSelection,
): Promise<void> {
  await ensureBackendProject(workspace, selection)
  await patchFrontendPackage(workspace, selection)
  await patchBackendPackage(workspace, selection)
  await writeGitignore(workspace, selection)
  await writePipeline(workspace, request, selection)
  await writeReadme(workspace, request, selection)
  if (selection.includeDocker) {
    await writeDocker(workspace, request, selection)
  }
}

export async function writeFixtureProject(
  workspace: string,
  request: InitJobRequest,
  selection: InitSelection,
): Promise<void> {
  await mkdirp(path.join(workspace, 'frontend', 'src'))
  await mkdirp(path.join(workspace, 'backend'))
  await writeFile(path.join(workspace, 'frontend', 'package.json'), JSON.stringify({
    scripts: { dev: 'vite', build: 'vite build' },
    dependencies: frontendDependencies(selection),
    devDependencies: {},
    engines: { node: `>=${selection.frontend.runtimeVersion}` },
  }, null, 2))
  await writeFile(path.join(workspace, 'frontend', 'src', 'main.ts'), 'console.log("Scaffy frontend");\n')
  await writeFile(path.join(workspace, 'backend', backendManifestName(selection)), backendManifest(selection))
  await postProcessProject(workspace, request, selection)
}

function frontendCommands(workspace: string, frontend: InitSelection['frontend']): CommandSpec[] {
  if (frontend.id === 'react') {
    return [npmCommand(workspace, {
      executable: 'npm',
      args: ['create', 'vite@latest', 'frontend', '--', '--template', 'react-ts'],
      label: 'Create React frontend',
    })]
  }
  if (frontend.id === 'vue') {
    return [npmCommand(workspace, {
      executable: 'npm',
      args: ['create', 'vite@latest', 'frontend', '--', '--template', 'vue-ts'],
      label: 'Create Vue frontend',
    })]
  }
  if (frontend.id === 'angular') {
    return [npmCommand(workspace, {
      executable: 'npx',
      args: ['-y', `@angular/cli@${frontend.version}`, 'new', 'frontend', '--defaults', '--skip-git', '--routing', '--style', 'css', '--directory', 'frontend'],
      label: 'Create Angular frontend',
    })]
  }
  throw new Error(`Unsupported frontend adapter: ${frontend.id}`)
}

function backendCommands(workspace: string, backend: InitSelection['backend']): CommandSpec[] {
  if (backend.id === 'nestjs') {
    return [npmCommand(workspace, {
      executable: 'npx',
      args: ['-y', `@nestjs/cli@${backend.version}`, 'new', 'backend', '--package-manager', 'npm', '--skip-git'],
      label: 'Create NestJS backend',
    })]
  }
  if (backend.id === 'dotnet') {
    return [{
      executable: 'dotnet',
      args: ['new', 'webapi', '-n', 'Backend', '-o', 'backend', '--framework', `net${backend.version}.0`],
      cwd: workspace,
      timeoutMs: CLI_TIMEOUT_MS,
      label: 'Create .NET backend',
      env: { ...process.env, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT: '1' },
    }]
  }
  if (backend.id === 'spring-boot') {
    return []
  }
  throw new Error(`Unsupported backend adapter: ${backend.id}`)
}

function npmCommand(
  workspace: string,
  spec: Pick<CommandSpec, 'executable' | 'args' | 'label'>,
): CommandSpec {
  const npmCache = path.join(workspace, '.scaffy', 'npm-cache')
  const npmHome = path.join(workspace, '.scaffy', 'home')
  return {
    ...spec,
    cwd: workspace,
    env: {
      CI: 'true',
      HOME: npmHome,
      NPM_CONFIG_CACHE: npmCache,
      npm_config_cache: npmCache,
      NPM_CONFIG_UPDATE_NOTIFIER: 'false',
      npm_config_update_notifier: 'false',
    },
    timeoutMs: CLI_TIMEOUT_MS,
  }
}

function commandFailure(spec: CommandSpec, error: unknown): Error {
  const err = error as {
    exitCode?: number
    shortMessage?: string
    stdout?: string
    stderr?: string
    message?: string
  }
  const output = [err.stderr, err.stdout].filter(Boolean).join('\n').trim()
  const reason = classifyCommandFailure(output || err.message || '')
  const command = `${spec.executable} ${spec.args.join(' ')}`
  const details = truncate(output || err.shortMessage || err.message || 'No command output was captured.')
  return new Error(`${spec.label} failed${err.exitCode ? ` (exit ${err.exitCode})` : ''}. ${reason}\n\nCommand: ${command}\n\n${details}`)
}

const TRANSIENT_FAILURE_PATTERN =
  /_cacache|NPM_CONFIG_CACHE|npm cache|EACCES|EEXIST|ENOTFOUND|ECONNRESET|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|socket hang up|network request|registry\.npmjs\.org|timed out|ESOCKETTIMEDOUT/i

export function isTransientFailure(message: string): boolean {
  return TRANSIENT_FAILURE_PATTERN.test(message)
}

function classifyCommandFailure(output: string): string {
  if (/_cacache|NPM_CONFIG_CACHE|npm cache|npm error code EACCES|npm error code EEXIST/i.test(output)) {
    return 'npm failed while using its package cache. The generator now isolates npm cache per job; restart the generator and run this job again.'
  }
  if (/ENOTFOUND|ECONNRESET|ETIMEDOUT|network request|registry\.npmjs\.org/i.test(output)) {
    return 'The framework CLI could not reach its package registry. Check network access from the generator process.'
  }
  if (/command not found|ENOENT/i.test(output)) {
    return 'A required CLI/runtime is missing from the generator environment.'
  }
  return 'The framework CLI returned an error.'
}

function truncate(value: string): string {
  const compact = value
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.trim().length > 0)
    .join('\n')
  return compact.length > 2400 ? `${compact.slice(0, 2400)}\n... output truncated ...` : compact
}

async function ensureBackendProject(workspace: string, selection: InitSelection): Promise<void> {
  const backendDir = path.join(workspace, 'backend')
  await mkdirp(backendDir)

  if (selection.backend.id === 'spring-boot' && !(await pathExists(path.join(backendDir, 'pom.xml')))) {
    await mkdirp(path.join(backendDir, 'src', 'main', 'java', 'com', 'scaffy', 'app'))
    await writeFile(path.join(backendDir, 'pom.xml'), backendManifest(selection))
    await writeFile(
      path.join(backendDir, 'src', 'main', 'java', 'com', 'scaffy', 'app', 'Application.java'),
      `package com.scaffy.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
`,
    )
  }
}

async function patchFrontendPackage(workspace: string, selection: InitSelection): Promise<void> {
  const packagePath = path.join(workspace, 'frontend', 'package.json')
  if (!(await pathExists(packagePath))) return
  const pkg = await readJson(packagePath)
  pkg.engines = { ...(pkg.engines ?? {}), node: `>=${selection.frontend.runtimeVersion}` }
  pkg.dependencies = { ...(pkg.dependencies ?? {}), ...frontendDependencies(selection) }
  await writeJson(packagePath, pkg, { spaces: 2 })
}

async function patchBackendPackage(workspace: string, selection: InitSelection): Promise<void> {
  if (selection.backend.runtime !== 'node') return
  const packagePath = path.join(workspace, 'backend', 'package.json')
  if (!(await pathExists(packagePath))) return
  const pkg = await readJson(packagePath)
  pkg.engines = { ...(pkg.engines ?? {}), node: `>=${selection.backend.runtimeVersion}` }
  await writeJson(packagePath, pkg, { spaces: 2 })
}

function frontendDependencies(selection: InitSelection): Record<string, string> {
  if (selection.frontend.id === 'react') {
    return {
      react: `^${selection.frontend.version}.0.0`,
      'react-dom': `^${selection.frontend.version}.0.0`,
    }
  }
  if (selection.frontend.id === 'vue') {
    return { vue: '^3.5.0' }
  }
  return {}
}

async function writeGitignore(workspace: string, selection: InitSelection): Promise<void> {
  // Root-level ignores cover cross-cutting artifacts. The frontend (Vite /
  // Angular) and NestJS backend scaffolders emit their own subfolder
  // .gitignore, so we only fill the gaps: the root, plus backends whose CLIs
  // produce no .gitignore (.NET and the hand-written Spring Boot project).
  await writeFile(path.join(workspace, '.gitignore'), `# Dependencies
node_modules/

# Build output
dist/
build/
out/

# Environment
.env
.env.*
!.env.example

# Logs
*.log
npm-debug.log*
yarn-error.log*

# OS files
.DS_Store
Thumbs.db

# Editor
.idea/
.vscode/
*.swp
`)

  const backendGitignore = backendGitignoreContents(selection)
  if (backendGitignore) {
    await writeFile(path.join(workspace, 'backend', '.gitignore'), backendGitignore)
  }
}

function backendGitignoreContents(selection: InitSelection): string | null {
  if (selection.backend.id === 'dotnet') {
    return `bin/
obj/
*.user
`
  }
  if (selection.backend.id === 'spring-boot') {
    return `target/
*.class
HELP.md
`
  }
  return null
}

async function writePipeline(workspace: string, request: InitJobRequest, selection: InitSelection): Promise<void> {
  if (selection.pipeline.id === 'github-actions') {
    const workflowDir = path.join(workspace, '.github', 'workflows')
    await mkdirp(workflowDir)
    await writeFile(path.join(workflowDir, 'ci.yml'), githubActions(request, selection))
    return
  }
  await writeFile(path.join(workspace, '.gitlab-ci.yml'), gitlabCi(request, selection))
}

async function writeReadme(workspace: string, request: InitJobRequest, selection: InitSelection): Promise<void> {
  await writeFile(path.join(workspace, 'README.md'), `# ${request.projectName}

Generated by Scaffy.

- Frontend: ${selection.frontend.versionLabel} on ${selection.frontend.runtimeLabel}
- Backend: ${selection.backend.versionLabel} on ${selection.backend.runtimeLabel}
- Pipeline: ${selection.pipeline.name}
- Maturity preset: ${selection.pipelineMaturity.label}
- Docker support: ${selection.includeDocker ? 'included' : 'not included'}

## Run locally

\`\`\`sh
cd frontend && npm install && npm run dev
\`\`\`
${selection.includeDocker ? `
## Run with Docker Compose

\`\`\`sh
docker compose up --build
\`\`\`
` : ''}
`)
}

async function writeDocker(workspace: string, request: InitJobRequest, selection: InitSelection): Promise<void> {
  const frontendPort = selection.frontend.id === 'angular' ? '4200' : '5173'
  const backendPort = selection.backend.runtime === 'node' ? '3000' : selection.backend.runtime === 'java' ? '8080' : '8080'

  await writeFile(path.join(workspace, 'frontend', 'Dockerfile'), `FROM node:${selection.frontend.runtimeVersion}-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE ${frontendPort}
${frontendDockerCommand(selection)}
`)

  const backendBase = selection.backend.runtime === 'node'
    ? `node:${selection.backend.runtimeVersion}-alpine`
    : selection.backend.runtime === 'java'
      ? `maven:3.9-eclipse-temurin-${selection.backend.runtimeVersion}`
      : `mcr.microsoft.com/dotnet/sdk:${selection.backend.runtimeVersion}.0-alpine`

  await writeFile(path.join(workspace, 'backend', 'Dockerfile'), `FROM ${backendBase}
WORKDIR /app
COPY . .
${backendDockerCommand(selection)}
EXPOSE ${backendPort}
`)

  await writeFile(path.join(workspace, 'docker-compose.yml'), `services:
  frontend:
    build:
      context: ./frontend
    ports:
      - "${frontendPort}:${frontendPort}"
    depends_on:
      - backend

  backend:
    build:
      context: ./backend
    ports:
      - "${backendPort}:${backendPort}"

name: ${request.projectName}
`)
}

function githubActions(request: InitJobRequest, selection: InitSelection): string {
  const level = selection.pipelineMaturity.level
  const jobs = [
    githubFrontendJob(selection),
    level >= 2 ? githubBackendTestJob(selection) : githubBackendBuildJob(selection),
    level >= 3 ? githubDockerJob(request, selection) : '',
    level >= 3 ? githubStackValidationJob() : '',
    level >= 4 ? githubSecurityJob() : '',
    level >= 4 ? githubDeploymentJob() : '',
  ].filter(Boolean).join('\n\n')

  return `name: CI

on:
  push:
    branches: [main]
${level >= 3 ? githubPathFilters('    ') : ''}
  pull_request:
${level >= 3 ? githubPathFilters('    ') : ''}

permissions:
  contents: read
  security-events: write

concurrency:
  group: ${gh('github.workflow')}-${gh('github.ref')}
  cancel-in-progress: true

jobs:
${jobs}
`
}

function gitlabCi(request: InitJobRequest, selection: InitSelection): string {
  const level = selection.pipelineMaturity.level
  return `stages:
  - build
${level >= 2 ? '  - test\n  - package' : ''}${level >= 3 ? '\n  - docker' : ''}${level >= 4 ? '\n  - security\n  - deploy' : ''}

variables:
  FRONTEND_NODE_VERSION: "${selection.frontend.runtimeVersion}"
${gitlabBackendVariables(selection)}

${gitlabFrontendBuild(selection)}
${level === 1 ? `\n${gitlabBackendBuild(selection)}` : ''}
${level >= 2 ? `\n${gitlabBackendTest(selection)}\n${gitlabPackageArtifacts(selection)}` : ''}
${level >= 3 ? `\n${gitlabDockerBuild(request, selection)}\n${gitlabStackValidation()}` : ''}
${level >= 4 ? `\n${gitlabSecurityScan()}\n${gitlabDeployPlaceholder()}` : ''}
`
}

function githubFrontendJob(selection: InitSelection): string {
  const level = selection.pipelineMaturity.level
  return `  frontend-build:
    name: Frontend build
    runs-on: ${level >= 4 ? gh('matrix.os') : 'ubuntu-24.04'}
    timeout-minutes: 15
    permissions:
      contents: read
    concurrency:
      group: frontend-${gh('github.ref')}
      cancel-in-progress: true
${level >= 4 ? `    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-24.04, ubuntu-22.04]
        node-version: [${selection.frontend.runtimeVersion}, ${alternateNodeVersion(selection.frontend.runtimeVersion)}]
` : ''}
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: ${level >= 4 ? gh('matrix.node-version') : selection.frontend.runtimeVersion}
${level >= 3 ? '          cache: npm\n          cache-dependency-path: frontend/package-lock.json' : ''}
      - name: Install frontend dependencies
        run: cd frontend && npm ci
      - name: Build frontend
        run: cd frontend && npm run build${level >= 2 ? `
      - name: Upload frontend artifact
        if: ${gh("github.event_name != 'pull_request' || github.repository_owner == github.event.pull_request.head.repo.owner.login")}
        uses: actions/upload-artifact@v4
        with:
          name: frontend-dist-${gh('github.sha')}
          path: frontend/dist
          if-no-files-found: ignore` : ''}`
}

function githubBackendBuildJob(selection: InitSelection): string {
  return `  backend-build:
    name: Backend build
    runs-on: ubuntu-24.04
    timeout-minutes: 15
    permissions:
      contents: read
    concurrency:
      group: backend-${gh('github.ref')}
      cancel-in-progress: true
    steps:
      - name: Checkout
        uses: actions/checkout@v4
${githubBackendSetupSteps(selection, false)}`
}

function githubBackendTestJob(selection: InitSelection): string {
  return `  backend-test:
    name: Backend test
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    needs: frontend-build
    permissions:
      contents: read
    concurrency:
      group: backend-${gh('github.ref')}
      cancel-in-progress: true
    steps:
      - name: Checkout
        uses: actions/checkout@v4
${githubBackendSetupSteps(selection, true)}
      - name: Upload backend artifact
        if: ${gh("github.event_name != 'pull_request' || github.repository_owner == github.event.pull_request.head.repo.owner.login")}
        uses: actions/upload-artifact@v4
        with:
          name: backend-artifact-${gh('github.sha')}
          path: ${backendArtifactPath(selection)}
          if-no-files-found: ignore`
}

function githubBackendSetupSteps(selection: InitSelection, test: boolean): string {
  if (selection.backend.runtime === 'node') {
    return `      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: ${selection.backend.runtimeVersion}
${selection.pipelineMaturity.level >= 3 ? '          cache: npm\n          cache-dependency-path: backend/package-lock.json' : ''}
      - name: Install backend dependencies
        run: cd backend && npm ci
      - name: ${test ? 'Test backend' : 'Build backend'}
        run: cd backend && npm run ${test ? 'test' : 'build'}`
  }
  if (selection.backend.runtime === 'java') {
    return `      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${selection.backend.runtimeVersion}
${selection.pipelineMaturity.level >= 3 ? '          cache: maven' : ''}
      - name: ${test ? 'Test backend' : 'Build backend'}
        run: cd backend && mvn ${test ? 'test' : 'package'}`
  }
  return `      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: ${selection.backend.runtimeVersion}.x
      - name: Restore backend dependencies
        run: cd backend && dotnet restore
      - name: ${test ? 'Test backend' : 'Build backend'}
        run: cd backend && dotnet ${test ? 'test --no-restore' : 'build --no-restore'}`
}

function githubDockerJob(request: InitJobRequest, selection: InitSelection): string {
  return `  docker-build:
    name: Docker image validation
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    needs: backend-test
    permissions:
      contents: read
    concurrency:
      group: docker-${gh('github.ref')}
      cancel-in-progress: true
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Build frontend image
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          push: false
          tags: ${request.projectName}-frontend:${gh('github.sha')}
      - name: Build backend image
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          push: false
          tags: ${request.projectName}-backend:${gh('github.sha')}`
}

function githubStackValidationJob(): string {
  return `  stack-validation:
    name: Compose stack validation
    runs-on: ubuntu-24.04
    timeout-minutes: 15
    needs: docker-build
    permissions:
      contents: read
    concurrency:
      group: stack-${gh('github.ref')}
      cancel-in-progress: true
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Start local stack
        run: docker compose up --build -d
      - name: Smoke test backend
        run: curl --fail http://localhost:8080/health || true
      - name: Stop local stack
        if: ${gh('always()')}
        run: docker compose down`
}

function githubSecurityJob(): string {
  return `  security-scan:
    name: Security scan
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    needs: docker-build
    permissions:
      contents: read
      security-events: write
    concurrency:
      group: security-${gh('github.ref')}
      cancel-in-progress: true
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Install Python security tools
        run: python -m pip install --user semgrep checkov
      - name: Run Semgrep SAST
        run: semgrep ci || true
      - name: Run dependency audit
        run: npm audit --audit-level=high || true
      - name: Run Checkov policy scan
        run: checkov -d . || true
      - name: Run Gitleaks secret scan
        run: gitleaks detect --source . --no-git || true
      - name: Run Trivy filesystem scan
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          scan-ref: .
          format: sarif
          output: trivy-results.sarif
      - name: Upload security SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy-results.sarif`
}

function githubDeploymentJob(): string {
  return `  deploy-preview:
    name: Deployment placeholder
    runs-on: ubuntu-24.04
    timeout-minutes: 10
    needs: security-scan
    permissions:
      contents: read
    concurrency:
      group: deploy-${gh('github.ref')}
      cancel-in-progress: true
    environment: preview
    if: ${gh("github.ref == 'refs/heads/main'")}
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Pull candidate image placeholder
        run: docker pull ghcr.io/${gh('github.repository')}/preview:${gh('github.sha')} || true
      - name: Start preview stack
        run: docker compose up --build -d
      - name: Smoke test preview
        run: curl --fail http://localhost:8080/health || true
      - name: Roll back preview stack
        if: ${gh('failure()')}
        run: docker compose down`
}

function githubPathFilters(indent: string): string {
  return `${indent}paths:
${indent}  - 'frontend/**'
${indent}  - 'backend/**'
${indent}  - 'docker-compose.yml'
${indent}  - '.github/workflows/**'`
}

function gitlabFrontendBuild(selection: InitSelection): string {
  return `frontend_build:
  image: node:${selection.frontend.runtimeVersion}
  stage: build
  timeout: 15m
${gitlabRules(selection)}
${selection.pipelineMaturity.level >= 3 ? `  cache:
    key: frontend-npm
    paths:
      - frontend/.npm/
` : ''}  before_script:
    - cd frontend
    - npm ci${selection.pipelineMaturity.level >= 3 ? ' --cache .npm --prefer-offline' : ''}
  script:
    - npm run build
${selection.pipelineMaturity.level >= 2 ? `  artifacts:
    name: "frontend-dist-$CI_COMMIT_SHORT_SHA"
    paths:
      - frontend/dist/
    when: always
    expire_in: 7 days
` : ''}`
}

function gitlabBackendBuild(selection: InitSelection): string {
  const image = selection.backend.runtime === 'node'
    ? `node:${selection.backend.runtimeVersion}`
    : selection.backend.runtime === 'java'
      ? `maven:3.9-eclipse-temurin-${selection.backend.runtimeVersion}`
      : `mcr.microsoft.com/dotnet/sdk:${selection.backend.runtimeVersion}.0`
  return `backend_build:
  image: ${image}
  stage: build
  timeout: 15m
${gitlabRules(selection)}
  script:
${gitlabBackendCommands(selection, false)}`
}

function gitlabBackendTest(selection: InitSelection): string {
  const image = selection.backend.runtime === 'node'
    ? `node:${selection.backend.runtimeVersion}`
    : selection.backend.runtime === 'java'
      ? `maven:3.9-eclipse-temurin-${selection.backend.runtimeVersion}`
      : `mcr.microsoft.com/dotnet/sdk:${selection.backend.runtimeVersion}.0`
  return `backend_test:
  image: ${image}
  stage: test
  timeout: 20m
${gitlabRules(selection)}
  script:
${gitlabBackendCommands(selection, true)}
  artifacts:
    name: "backend-artifact-$CI_COMMIT_SHORT_SHA"
    paths:
      - ${backendArtifactPath(selection)}
    when: always
    expire_in: 7 days
`
}

function gitlabPackageArtifacts(selection: InitSelection): string {
  return `package_artifacts:
  image: alpine:3.20
  stage: package
  timeout: 10m
  needs:
    - frontend_build
    - backend_test
  script:
    - tar -czf scaffy-artifacts-$CI_COMMIT_SHORT_SHA.tar.gz frontend backend
  artifacts:
    name: "scaffy-artifacts-$CI_COMMIT_SHORT_SHA"
    paths:
      - scaffy-artifacts-$CI_COMMIT_SHORT_SHA.tar.gz
    expire_in: 7 days`
}

function gitlabDockerBuild(request: InitJobRequest, selection: InitSelection): string {
  return `docker_build:
  image: docker:27
  services:
    - docker:27-dind
  stage: docker
  timeout: 20m
  needs:
    - package_artifacts
${gitlabRules(selection)}
  script:
    - docker build -t ${request.projectName}-frontend:$CI_COMMIT_SHORT_SHA frontend
    - docker build -t ${request.projectName}-backend:$CI_COMMIT_SHORT_SHA backend`
}

function gitlabStackValidation(): string {
  return `stack_validation:
  image: docker:27
  services:
    - docker:27-dind
  stage: docker
  timeout: 15m
  needs:
    - docker_build
  script:
    - docker compose up --build -d
    - wget -qO- http://localhost:8080/health || true
    - docker compose down`
}

function gitlabSecurityScan(): string {
  return `security_scan:
  image: aquasec/trivy:latest
  stage: security
  timeout: 20m
  script:
    - trivy fs --format table --exit-code 0 .`
}

function gitlabDeployPlaceholder(): string {
  return `deploy_preview:
  image: alpine:3.20
  stage: deploy
  timeout: 10m
  environment:
    name: preview
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
      when: manual
  script:
    - echo "Connect your deployment target here. Keep this job manual until credentials are configured."`
}

function gitlabBackendVariables(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') return `  BACKEND_NODE_VERSION: "${selection.backend.runtimeVersion}"`
  if (selection.backend.runtime === 'java') return `  BACKEND_JAVA_VERSION: "${selection.backend.runtimeVersion}"`
  return `  BACKEND_DOTNET_VERSION: "${selection.backend.runtimeVersion}"`
}

function gitlabBackendCommands(selection: InitSelection, test: boolean): string {
  if (selection.backend.runtime === 'node') {
    return `    - cd backend
    - npm ci
    - npm run ${test ? 'test' : 'build'}`
  }
  if (selection.backend.runtime === 'java') {
    return `    - cd backend
    - mvn ${test ? 'test' : 'package'}`
  }
  return `    - cd backend
    - dotnet restore
    - dotnet ${test ? 'test --no-restore' : 'build --no-restore'}`
}

function gitlabRules(selection: InitSelection): string {
  if (selection.pipelineMaturity.level < 3) return ''
  return `  rules:
    - changes:
        - frontend/**/*
        - backend/**/*
        - docker-compose.yml
        - .gitlab-ci.yml
`
}

function backendArtifactPath(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') return 'backend/dist/'
  if (selection.backend.runtime === 'java') return 'backend/target/*.jar'
  return 'backend/bin/Release/'
}

function backendDockerCommand(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') {
    return `RUN npm install
RUN npm run build
CMD ["npm", "run", "start"]`
  }
  if (selection.backend.runtime === 'java') {
    return `RUN mvn package -DskipTests
CMD ["java", "-jar", "target/backend-0.0.1-SNAPSHOT.jar"]`
  }
  return `ENV ASPNETCORE_URLS=http://+:8080
RUN dotnet restore
RUN dotnet publish -c Release -o /app/out
CMD ["dotnet", "/app/out/Backend.dll"]`
}

function frontendDockerCommand(selection: InitSelection): string {
  if (selection.frontend.id === 'angular') {
    return 'CMD ["npm", "run", "start", "--", "--host", "0.0.0.0"]'
  }
  return 'CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]'
}

function alternateNodeVersion(version: string): string {
  if (version === '24') return '22'
  if (version === '22') return '20'
  return '22'
}

function gh(expression: string): string {
  return `\${{ ${expression} }}`
}

function backendManifestName(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') return 'package.json'
  if (selection.backend.runtime === 'java') return 'pom.xml'
  return 'Backend.csproj'
}

function backendManifest(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') {
    return JSON.stringify({ scripts: { build: 'nest build' }, engines: { node: `>=${selection.backend.runtimeVersion}` } }, null, 2)
  }
  if (selection.backend.runtime === 'java') {
    return `<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>${selection.backend.version}.0</version>
    <relativePath/>
  </parent>
  <groupId>com.scaffy</groupId>
  <artifactId>backend</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties>
    <java.version>${selection.backend.runtimeVersion}</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
`
  }
  return `<Project Sdk="Microsoft.NET.Sdk.Web"><PropertyGroup><TargetFramework>net${selection.backend.version}.0</TargetFramework></PropertyGroup></Project>\n`
}
