import assert from 'node:assert/strict'
import test from 'node:test'
import * as cache from './cache.js'
import type { InitJobRequest } from './types.js'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeRequest(overrides: Partial<InitJobRequest> = {}): InitJobRequest {
  return {
    projectName: 'demo-app',
    frontend: 'angular',
    frontendVersion: '19',
    frontendRuntime: 'node-22',
    backend: 'spring-boot',
    backendVersion: '4.0',
    backendRuntime: 'java-21',
    pipeline: 'github-actions',
    pipelineMaturity: 'l2',
    includeDocker: false,
    ...overrides,
  }
}

function makeBuffer(content = 'fake-zip'): Buffer {
  return Buffer.from(content)
}

// Reset between tests so they don't bleed into each other.
function resetCache() {
  cache.clearAll()
}

// ---------------------------------------------------------------------------
// requestKey
// ---------------------------------------------------------------------------

test('requestKey is deterministic for the same request', () => {
  const req = makeRequest()
  assert.equal(cache.requestKey(req), cache.requestKey(req))
})

test('requestKey is stable regardless of property insertion order', () => {
  const a = makeRequest()
  const b = {
    pipeline: a.pipeline,
    projectName: a.projectName,
    frontend: a.frontend,
    frontendVersion: a.frontendVersion,
    frontendRuntime: a.frontendRuntime,
    backend: a.backend,
    backendVersion: a.backendVersion,
    backendRuntime: a.backendRuntime,
    pipelineMaturity: a.pipelineMaturity,
    includeDocker: a.includeDocker,
  } as InitJobRequest
  assert.equal(cache.requestKey(a), cache.requestKey(b))
})

test('requestKey differs when projectName differs', () => {
  const a = makeRequest({ projectName: 'app-one' })
  const b = makeRequest({ projectName: 'app-two' })
  assert.notEqual(cache.requestKey(a), cache.requestKey(b))
})

test('requestKey differs when frontend differs', () => {
  const a = makeRequest({ frontend: 'angular' })
  const b = makeRequest({ frontend: 'react' })
  assert.notEqual(cache.requestKey(a), cache.requestKey(b))
})

test('requestKey differs when backend differs', () => {
  const a = makeRequest({ backend: 'spring-boot' })
  const b = makeRequest({ backend: 'nestjs' })
  assert.notEqual(cache.requestKey(a), cache.requestKey(b))
})

test('requestKey is a 64-character hex SHA-256', () => {
  const key = cache.requestKey(makeRequest())
  assert.match(key, /^[0-9a-f]{64}$/)
})

// ---------------------------------------------------------------------------
// get / put basics
// ---------------------------------------------------------------------------

test('get returns null on miss', () => {
  resetCache()
  assert.equal(cache.get('nonexistent-key'), null)
})

test('put then get returns the stored buffer', () => {
  resetCache()
  const key = cache.requestKey(makeRequest())
  const buf = makeBuffer()
  cache.put(key, buf)
  const result = cache.get(key)
  assert.ok(result !== null)
  assert.deepEqual(result, buf)
})

test('get returns null after evict', () => {
  resetCache()
  const key = cache.requestKey(makeRequest())
  cache.put(key, makeBuffer())
  cache.evict(key)
  assert.equal(cache.get(key), null)
})

test('evict unknown key returns false and does not throw', () => {
  resetCache()
  const removed = cache.evict('does-not-exist')
  assert.equal(removed, false)
})

test('evict known key returns true', () => {
  resetCache()
  const key = cache.requestKey(makeRequest())
  cache.put(key, makeBuffer())
  assert.equal(cache.evict(key), true)
})

// ---------------------------------------------------------------------------
// clearAll
// ---------------------------------------------------------------------------

test('clearAll removes all entries', () => {
  resetCache()
  cache.put(cache.requestKey(makeRequest({ projectName: 'a' })), makeBuffer('a'))
  cache.put(cache.requestKey(makeRequest({ projectName: 'b' })), makeBuffer('b'))
  cache.put(cache.requestKey(makeRequest({ projectName: 'c' })), makeBuffer('c'))
  cache.clearAll()
  assert.equal(cache.stats().totalSize, 0)
})

// ---------------------------------------------------------------------------
// TTL expiry
// ---------------------------------------------------------------------------

test('entry is returned before TTL expires', () => {
  resetCache()
  const key = cache.requestKey(makeRequest())
  cache.put(key, makeBuffer())
  // Reading immediately — well within any TTL.
  assert.ok(cache.get(key) !== null)
})

test('entry appears expired when Date.now is advanced past TTL', () => {
  resetCache()
  const key = cache.requestKey(makeRequest({ projectName: 'ttl-test' }))
  cache.put(key, makeBuffer())

  // Simulate time passing well beyond the default 1-hour TTL.
  const realDateNow = Date.now
  Date.now = () => realDateNow() + 3_600_001
  try {
    assert.equal(cache.get(key), null)
  } finally {
    Date.now = realDateNow
  }
})

test('expired entry is evicted from the store on access', () => {
  resetCache()
  const key = cache.requestKey(makeRequest({ projectName: 'ttl-evict' }))
  cache.put(key, makeBuffer())

  const realDateNow = Date.now
  Date.now = () => realDateNow() + 3_600_001
  try {
    cache.get(key) // triggers internal eviction
  } finally {
    Date.now = realDateNow
  }

  // After the faked clock is restored the entry should be gone.
  assert.equal(cache.stats().totalSize, 0)
})

// ---------------------------------------------------------------------------
// Max-entries eviction
// ---------------------------------------------------------------------------

test('oldest entry is evicted when store exceeds max capacity', () => {
  resetCache()
  // Fill the store to capacity (50 entries default).
  const MAX = 50
  const keys: string[] = []
  for (let i = 0; i < MAX; i++) {
    const key = cache.requestKey(makeRequest({ projectName: `proj-${i}` }))
    cache.put(key, makeBuffer(`zip-${i}`))
    keys.push(key)
  }

  assert.equal(cache.stats().totalSize, MAX)

  // Adding one more should evict the oldest.
  const extra = cache.requestKey(makeRequest({ projectName: 'extra' }))
  cache.put(extra, makeBuffer('extra'))

  assert.equal(cache.stats().totalSize, MAX)
  // Oldest entry (keys[0]) should be gone.
  assert.equal(cache.get(keys[0]!), null)
  // Newest entry should be present.
  assert.ok(cache.get(extra) !== null)
})

// ---------------------------------------------------------------------------
// stats
// ---------------------------------------------------------------------------

test('stats reports hit and miss counts', () => {
  resetCache()
  const key = cache.requestKey(makeRequest({ projectName: 'stats-test' }))
  cache.put(key, makeBuffer())

  cache.get(key)          // hit
  cache.get('no-such-key') // miss

  const s = cache.stats()
  assert.ok(s.hits >= 1)
  assert.ok(s.misses >= 1)
})

test('stats reflects cache configuration', () => {
  const s = cache.stats()
  assert.equal(s.enabled, true)
  assert.equal(s.ttlMs, 3_600_000)
  assert.equal(s.maxEntries, 50)
})

test('stats validEntries counts only non-expired entries', () => {
  resetCache()
  cache.put(cache.requestKey(makeRequest({ projectName: 'valid' })), makeBuffer())

  const s = cache.stats()
  assert.equal(s.validEntries, 1)
  assert.equal(s.expiredEntries, 0)
})

test('stats expiredEntries counts expired entries without evicting them', () => {
  resetCache()
  const key = cache.requestKey(makeRequest({ projectName: 'to-expire' }))
  cache.put(key, makeBuffer())

  const realDateNow = Date.now
  Date.now = () => realDateNow() + 3_600_001
  try {
    const s = cache.stats()
    assert.equal(s.expiredEntries, 1)
    assert.equal(s.validEntries, 0)
    // stats() does NOT evict — total size still 1.
    assert.equal(s.totalSize, 1)
  } finally {
    Date.now = realDateNow
  }
})
