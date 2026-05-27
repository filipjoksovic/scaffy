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
  await writePipeline(workspace, selection)
  await writeReadme(workspace, request, selection)
  if (selection.includeDocker) {
    await writeDocker(workspace, selection)
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

async function writePipeline(workspace: string, selection: InitSelection): Promise<void> {
  if (selection.pipeline.id === 'github-actions') {
    const workflowDir = path.join(workspace, '.github', 'workflows')
    await mkdirp(workflowDir)
    await writeFile(path.join(workflowDir, 'ci.yml'), githubActions(selection))
    return
  }
  await writeFile(path.join(workspace, '.gitlab-ci.yml'), gitlabCi(selection))
}

async function writeReadme(workspace: string, request: InitJobRequest, selection: InitSelection): Promise<void> {
  await writeFile(path.join(workspace, 'README.md'), `# ${request.projectName}

Generated by Scaffy.

- Frontend: ${selection.frontend.versionLabel} on ${selection.frontend.runtimeLabel}
- Backend: ${selection.backend.versionLabel} on ${selection.backend.runtimeLabel}
- Pipeline: ${selection.pipeline.name}

## Run locally

\`\`\`sh
cd frontend && npm install && npm run dev
\`\`\`
`)
}

async function writeDocker(workspace: string, selection: InitSelection): Promise<void> {
  await writeFile(path.join(workspace, 'frontend', 'Dockerfile'), `FROM node:${selection.frontend.runtimeVersion}-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
`)

  const backendBase = selection.backend.runtime === 'node'
    ? `node:${selection.backend.runtimeVersion}-alpine`
    : selection.backend.runtime === 'java'
      ? `eclipse-temurin:${selection.backend.runtimeVersion}-jdk`
      : `mcr.microsoft.com/dotnet/sdk:${selection.backend.runtimeVersion}.0-alpine`

  await writeFile(path.join(workspace, 'backend', 'Dockerfile'), `FROM ${backendBase}
WORKDIR /app
COPY . .
`)
}

function githubActions(selection: InitSelection): string {
  return `name: CI

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: ${selection.frontend.runtimeVersion}
      - name: Build frontend
        run: cd frontend && npm ci && npm run build
${backendSetup(selection)}
`
}

function gitlabCi(selection: InitSelection): string {
  return `stages:
  - build

frontend:
  image: node:${selection.frontend.runtimeVersion}
  stage: build
  script:
    - cd frontend
    - npm ci
    - npm run build
`
}

function backendSetup(selection: InitSelection): string {
  if (selection.backend.runtime === 'node') {
    return `      - uses: actions/setup-node@v4
        with:
          node-version: ${selection.backend.runtimeVersion}
      - name: Build backend
        run: cd backend && npm ci && npm run build`
  }
  if (selection.backend.runtime === 'java') {
    return `      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${selection.backend.runtimeVersion}
      - name: Build backend
        run: cd backend && ./mvnw test`
  }
  return `      - uses: actions/setup-dotnet@v4
        with:
          dotnet-version: ${selection.backend.runtimeVersion}.x
      - name: Build backend
        run: cd backend && dotnet test`
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
