import assert from 'node:assert/strict'
import { mkdtemp, readFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { validateSelection } from './catalog.js'
import { buildCommandPlan, frontendLockfileCommand, writeFixtureProject } from './commands.js'

test('builds allowlisted command specs for a supported stack', () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'nestjs',
    backendVersion: '11',
    backendRuntime: 'node-22',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: true,
  }

  const selection = validateSelection(request)
  const commands = buildCommandPlan('/tmp/work', request, selection)

  assert.deepEqual(commands.map((command) => command.executable), ['npm', 'npx'])
  assert.ok(commands.every((command) => !command.args.some((arg) => arg.includes('&&'))))
  assert.ok(commands.every((command) => command.cwd === '/tmp/work'))
})

test('rejects unsupported runtime combinations', () => {
  assert.throws(() =>
    validateSelection({
      projectName: 'demo-app',
      frontend: 'angular',
      frontendVersion: '18',
      frontendRuntime: 'node-24',
      backend: 'dotnet',
      backendVersion: '10',
      backendRuntime: 'dotnet-10',
      pipeline: 'github-actions',
      pipelineMaturity: 'l2',
      includeDocker: false,
    }),
  )
})

test('l4 GitHub fixture includes governed pipeline and compose output', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l4',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const selection = validateSelection(request)

  await writeFixtureProject(root, request, selection)

  const workflow = await readFile(path.join(root, '.github', 'workflows', 'ci.yml'), 'utf8')
  const compose = await readFile(path.join(root, 'docker-compose.yml'), 'utf8')

  assert.match(workflow, /timeout-minutes:/)
  assert.match(workflow, /permissions:/)
  assert.match(workflow, /strategy:/)
  assert.match(workflow, /node-version: \[/)
  assert.match(workflow, /docker-build:/)
  assert.match(workflow, /security-scan:/)
  assert.match(workflow, /semgrep ci/)
  assert.match(workflow, /gitleaks detect/)
  assert.match(workflow, /checkov -d \./)
  assert.match(workflow, /deploy-preview:/)
  assert.match(workflow, /docker compose up --build -d/)
  assert.match(workflow, /curl --fail http:\/\/localhost:8080\/health/)
  assert.match(compose, /services:/)
})

test('l1 GitLab fixture stays minimal without forced Docker', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'vue',
    frontendVersion: '3',
    frontendRuntime: 'node-22',
    backend: 'nestjs',
    backendVersion: '11',
    backendRuntime: 'node-22',
    pipeline: 'gitlab-ci',
    pipelineMaturity: 'l1',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const selection = validateSelection(request)

  await writeFixtureProject(root, request, selection)

  const pipeline = await readFile(path.join(root, '.gitlab-ci.yml'), 'utf8')
  assert.match(pipeline, /frontend_build:/)
  assert.match(pipeline, /backend_build:/)
  assert.match(pipeline, /npm run build/)
  assert.doesNotMatch(pipeline, /docker_build:/)
  assert.equal(selection.includeDocker, false)
})

test('fixture frontend includes npm lockfile because generated CI uses npm ci', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'vue',
    frontendVersion: '3',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))

  await writeFixtureProject(root, request, validateSelection(request))

  const packageLock = JSON.parse(await readFile(path.join(root, 'frontend', 'package-lock.json'), 'utf8'))
  assert.equal(packageLock.lockfileVersion, 3)
  assert.equal(packageLock.packages[''].name, 'demo-app')
  assert.deepEqual(packageLock.packages[''].dependencies, { vue: '^3.5.0' })
})

test('runtime generation has a frontend lockfile command after package patching', () => {
  const command = frontendLockfileCommand('/tmp/work')

  assert.equal(command.executable, 'npm')
  assert.deepEqual(command.args, ['install', '--package-lock-only', '--ignore-scripts', '--no-audit', '--no-fund'])
  assert.equal(command.cwd, '/tmp/work/frontend')
  assert.equal(command.env?.CI, 'true')
})

test('l2 GitHub fixture emits scanner-recognized test and artifact signals', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'nestjs',
    backendVersion: '11',
    backendRuntime: 'node-22',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const selection = validateSelection(request)

  await writeFixtureProject(root, request, selection)

  const workflow = await readFile(path.join(root, '.github', 'workflows', 'ci.yml'), 'utf8')
  assert.match(workflow, /backend-build:/)
  assert.match(workflow, /npm run build/)
  assert.match(workflow, /backend-test:/)
  assert.match(workflow, /needs: backend-build/)
  assert.match(workflow, /npm run test/)
  assert.match(workflow, /actions\/upload-artifact@v4/)
  assert.match(workflow, /timeout-minutes:/)
  assert.match(workflow, /permissions:/)
  assert.doesNotMatch(workflow, /docker-build:/)
  assert.equal(selection.includeDocker, false)
})

test('l3 GitHub fixture emits structured delivery signals and compose output', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'vue',
    frontendVersion: '3',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l3',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const selection = validateSelection(request)

  await writeFixtureProject(root, request, selection)

  const workflow = await readFile(path.join(root, '.github', 'workflows', 'ci.yml'), 'utf8')
  const compose = await readFile(path.join(root, 'docker-compose.yml'), 'utf8')
  assert.match(workflow, /paths:/)
  assert.match(workflow, /cache: maven/)
  assert.match(workflow, /docker-build:/)
  assert.match(workflow, /docker\/build-push-action@v6/)
  assert.match(workflow, /stack-validation:/)
  assert.match(workflow, /docker compose up --build -d/)
  assert.match(workflow, /curl --fail http:\/\/localhost:8080\/health/)
  assert.match(compose, /services:/)
  assert.equal(selection.includeDocker, true)
})

test('writes a root .gitignore plus a backend .gitignore for .NET', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'angular',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'dotnet',
    backendVersion: '9',
    backendRuntime: 'dotnet-9',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  await writeFixtureProject(root, request, validateSelection(request))

  const rootIgnore = await readFile(path.join(root, '.gitignore'), 'utf8')
  assert.match(rootIgnore, /node_modules\//)
  assert.match(rootIgnore, /\.env/)
  assert.match(rootIgnore, /\.DS_Store/)

  const backendIgnore = await readFile(path.join(root, 'backend', '.gitignore'), 'utf8')
  assert.match(backendIgnore, /bin\//)
  assert.match(backendIgnore, /obj\//)
})

test('writes a backend .gitignore for Spring Boot', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  await writeFixtureProject(root, request, validateSelection(request))

  const backendIgnore = await readFile(path.join(root, 'backend', '.gitignore'), 'utf8')
  assert.match(backendIgnore, /target\//)
})

test('does not write a backend .gitignore for a Node backend', async () => {
  const request = {
    projectName: 'demo-app',
    frontend: 'react',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'nestjs',
    backendVersion: '11',
    backendRuntime: 'node-22',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
  }
  const root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  await writeFixtureProject(root, request, validateSelection(request))

  await assert.rejects(readFile(path.join(root, 'backend', '.gitignore'), 'utf8'))
})

test('l2 and l3 GitLab fixtures emit scanner-recognized progression signals', async () => {
  const base = {
    projectName: 'demo-app',
    frontend: 'angular',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'dotnet',
    backendVersion: '9',
    backendRuntime: 'dotnet-9',
    pipeline: 'gitlab-ci',
    includeDocker: false,
  }

  const l2Root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const l2Request = { ...base, pipelineMaturity: 'l2' }
  await writeFixtureProject(l2Root, l2Request, validateSelection(l2Request))
  const l2Pipeline = await readFile(path.join(l2Root, '.gitlab-ci.yml'), 'utf8')
  assert.match(l2Pipeline, /backend_build:/)
  assert.match(l2Pipeline, /dotnet build --no-restore/)
  assert.match(l2Pipeline, /backend_test:/)
  assert.match(l2Pipeline, /- backend_build/)
  assert.match(l2Pipeline, /dotnet test --no-restore/)
  assert.match(l2Pipeline, /package_artifacts:/)
  assert.match(l2Pipeline, /artifacts:/)
  assert.doesNotMatch(l2Pipeline, /docker_build:/)

  const l3Root = await mkdtemp(path.join(tmpdir(), 'scaffy-generator-test-'))
  const l3Request = { ...base, pipelineMaturity: 'l3' }
  const l3Selection = validateSelection(l3Request)
  await writeFixtureProject(l3Root, l3Request, l3Selection)
  const l3Pipeline = await readFile(path.join(l3Root, '.gitlab-ci.yml'), 'utf8')
  const compose = await readFile(path.join(l3Root, 'docker-compose.yml'), 'utf8')
  assert.match(l3Pipeline, /rules:/)
  assert.match(l3Pipeline, /cache:/)
  assert.match(l3Pipeline, /docker_build:/)
  assert.match(l3Pipeline, /stack_validation:/)
  assert.match(l3Pipeline, /docker compose up --build -d/)
  assert.match(l3Pipeline, /wget -qO- http:\/\/localhost:8080\/health/)
  assert.match(compose, /services:/)
  assert.equal(l3Selection.includeDocker, true)
})
