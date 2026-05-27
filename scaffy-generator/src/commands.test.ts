import assert from 'node:assert/strict'
import test from 'node:test'
import { validateSelection } from './catalog.js'
import { buildCommandPlan } from './commands.js'

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
      includeDocker: false,
    }),
  )
})
