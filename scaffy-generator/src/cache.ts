import { createHash } from 'node:crypto'
import type { InitJobRequest } from './types.js'

/**
 * In-memory ZIP result cache for the generator.
 *
 * Each entry stores the raw ZIP bytes for a particular InitRequest so that an
 * identical repeated request can be served without running the framework CLIs
 * or re-building the archive.
 *
 * Configuration via environment variables:
 *   SCAFFY_CACHE_TTL_MS      — entry lifetime in ms (default: 3 600 000 = 1 h)
 *   SCAFFY_CACHE_MAX_ENTRIES — max number of cached results (default: 50)
 *   SCAFFY_CACHE_DISABLED    — set to "true" to bypass the cache entirely
 */

const TTL_MS      = Number(process.env['SCAFFY_CACHE_TTL_MS']      ?? 3_600_000)
const MAX_ENTRIES = Number(process.env['SCAFFY_CACHE_MAX_ENTRIES']  ?? 50)
const DISABLED    = process.env['SCAFFY_CACHE_DISABLED'] === 'true'

interface CacheEntry {
  bytes:     Buffer
  createdAt: number
}

const store = new Map<string, CacheEntry>()

let hits   = 0
let misses = 0

// ------------------------------------------------------------------
// Public API
// ------------------------------------------------------------------

/** Compute a stable cache key for a given InitJobRequest. */
export function requestKey(request: InitJobRequest): string {
  const canonical = JSON.stringify(request, Object.keys(request).sort())
  return createHash('sha256').update(canonical).digest('hex')
}

/**
 * Returns the cached ZIP bytes for a given key, or `null` on a miss or
 * expired entry.
 */
export function get(key: string): Buffer | null {
  if (DISABLED) return null
  const entry = store.get(key)
  if (!entry) {
    misses++
    return null
  }
  if (Date.now() - entry.createdAt > TTL_MS) {
    store.delete(key)
    misses++
    return null
  }
  hits++
  return entry.bytes
}

/**
 * Stores ZIP bytes under the given key. If the store is at capacity the oldest
 * entry is evicted first (LRU-like: insertion order of Map guarantees this).
 */
export function put(key: string, bytes: Buffer): void {
  if (DISABLED) return
  if (store.size >= MAX_ENTRIES) {
    const oldest = store.keys().next().value
    if (oldest !== undefined) store.delete(oldest)
  }
  store.set(key, { bytes, createdAt: Date.now() })
}

/** Removes a single entry. Safe to call with an unknown key. */
export function evict(key: string): boolean {
  return store.delete(key)
}

/** Drops all cached entries. */
export function clearAll(): void {
  store.clear()
}

/** Current cache statistics snapshot. */
export function stats(): CacheStats {
  let validEntries = 0
  let expiredEntries = 0
  const now = Date.now()
  for (const entry of store.values()) {
    if (now - entry.createdAt > TTL_MS) expiredEntries++
    else validEntries++
  }
  return {
    enabled: !DISABLED,
    validEntries,
    expiredEntries,
    totalSize: store.size,
    hits,
    misses,
    ttlMs: TTL_MS,
    maxEntries: MAX_ENTRIES,
  }
}

export interface CacheStats {
  enabled:        boolean
  validEntries:   number
  expiredEntries: number
  totalSize:      number
  hits:           number
  misses:         number
  ttlMs:          number
  maxEntries:     number
}
