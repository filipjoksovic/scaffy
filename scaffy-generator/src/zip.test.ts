import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { promisify } from 'node:util'
import { mkdirp } from 'fs-extra/esm'
import { zipDirectory } from './zip.js'

const execFileAsync = promisify(execFile)

async function listZipEntries(zipPath: string): Promise<string[]> {
  const { stdout } = await execFileAsync('unzip', ['-Z1', zipPath])
  return stdout
    .split('\n')
    .map((line) => line.trim().replaceAll('\\', '/'))
    .filter((line) => line.length > 0 && !line.endsWith('/'))
}

async function buildWorkspace(): Promise<string> {
  const dir = await mkdtemp(path.join(tmpdir(), 'scaffy-zip-test-'))
  const workspace = path.join(dir, 'demo-app')

  await mkdirp(path.join(workspace, 'src'))
  await writeFile(path.join(workspace, 'src', 'index.ts'), 'export const x = 1\n')
  await writeFile(path.join(workspace, '.gitignore'), 'node_modules\n')

  // Dependency / build-output directories that must be excluded.
  await mkdirp(path.join(workspace, 'node_modules', 'left-pad'))
  await writeFile(path.join(workspace, 'node_modules', 'left-pad', 'index.js'), 'module.exports = 1\n')
  await mkdirp(path.join(workspace, 'src', 'node_modules'))
  await writeFile(path.join(workspace, 'src', 'node_modules', 'nested.js'), 'nested\n')
  await mkdirp(path.join(workspace, 'dist'))
  await writeFile(path.join(workspace, 'dist', 'bundle.js'), 'bundle\n')
  await mkdirp(path.join(workspace, 'target'))
  await writeFile(path.join(workspace, 'target', 'app.jar'), 'jar\n')
  await mkdirp(path.join(workspace, 'bin'))
  await writeFile(path.join(workspace, 'bin', 'app.dll'), 'dll\n')

  return workspace
}

test('zipDirectory excludes dependency and build-output directories', async () => {
  const workspace = await buildWorkspace()
  const root = path.dirname(workspace)
  const zipPath = path.join(root, 'demo-app.zip')

  try {
    await zipDirectory(workspace, zipPath)
    const entries = await listZipEntries(zipPath)

    assert.ok(entries.includes('src/index.ts'), 'source files are kept')
    assert.ok(entries.includes('.gitignore'), '.gitignore is kept')

    for (const excluded of [
      'node_modules/left-pad/index.js',
      'src/node_modules/nested.js',
      'dist/bundle.js',
      'target/app.jar',
      'bin/app.dll',
    ]) {
      assert.ok(!entries.includes(excluded), `${excluded} must be excluded`)
    }
  } finally {
    await rm(root, { recursive: true, force: true })
  }
})
