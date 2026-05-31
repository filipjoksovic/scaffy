import assert from 'node:assert/strict'
import test from 'node:test'
import { isTransientFailure } from './commands.js'

test('classifies network and registry errors as transient', () => {
  for (const message of [
    'getaddrinfo ENOTFOUND registry.npmjs.org',
    'request to https://registry.npmjs.org failed, reason: ECONNRESET',
    'npm error network request to the registry timed out',
    'socket hang up',
  ]) {
    assert.equal(isTransientFailure(message), true, message)
  }
})

test('classifies npm cache permission errors as transient', () => {
  assert.equal(isTransientFailure('npm error code EACCES on _cacache'), true)
  assert.equal(isTransientFailure('npm error EEXIST: file already exists in _cacache/index-v5'), true)
})

test('classifies an overall job timeout as transient', () => {
  assert.equal(isTransientFailure('Generation timed out after 300000ms'), true)
})

test('treats deterministic build errors as non-transient', () => {
  for (const message of [
    'Unsupported frontend adapter: svelte',
    'TypeScript error: cannot find name Foo',
    'Create NestJS backend failed (exit 1). The framework CLI returned an error.',
  ]) {
    assert.equal(isTransientFailure(message), false, message)
  }
})
