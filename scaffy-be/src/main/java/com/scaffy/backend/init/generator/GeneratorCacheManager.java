package com.scaffy.backend.init.generator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory cache for generator resources that are expensive to load from the
 * classpath on every request: raw artifact ZIP bytes and template file bytes.
 *
 * <p>Both caches are keyed by the classpath resource path string and hold the
 * raw bytes. Substitution is always done after the bytes leave the cache, so a
 * single cached copy serves requests with different project names.
 *
 * <p>Call {@link #clearAll()} after redeploying new artifacts or templates so
 * the next request picks up the fresh content.
 */
@Component
public class GeneratorCacheManager {

	private static final Logger log = LoggerFactory.getLogger(GeneratorCacheManager.class);

	private final ConcurrentHashMap<String, byte[]> artifactCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, byte[]> templateCache = new ConcurrentHashMap<>();

	private final AtomicLong artifactHits   = new AtomicLong();
	private final AtomicLong artifactMisses = new AtomicLong();
	private final AtomicLong templateHits   = new AtomicLong();
	private final AtomicLong templateMisses = new AtomicLong();

	// ------------------------------------------------------------------
	// Artifact cache
	// ------------------------------------------------------------------

	/**
	 * Returns the cached artifact bytes for {@code resourcePath}, or {@code null}
	 * on a cache miss.
	 */
	public byte[] getArtifact(String resourcePath) {
		byte[] cached = artifactCache.get(resourcePath);
		if (cached != null) {
			artifactHits.incrementAndGet();
			log.debug("Artifact cache HIT  {}", resourcePath);
		} else {
			artifactMisses.incrementAndGet();
			log.debug("Artifact cache MISS {}", resourcePath);
		}
		return cached;
	}

	/** Stores artifact bytes in the cache. A {@code null} value is ignored. */
	public void putArtifact(String resourcePath, byte[] bytes) {
		if (bytes != null) {
			artifactCache.put(resourcePath, bytes);
		}
	}

	/** Removes a single artifact entry so it is reloaded on the next request. */
	public void evictArtifact(String resourcePath) {
		artifactCache.remove(resourcePath);
		log.info("Evicted artifact cache entry: {}", resourcePath);
	}

	// ------------------------------------------------------------------
	// Template cache
	// ------------------------------------------------------------------

	/** Returns the cached template bytes for {@code resourcePath}, or {@code null}. */
	public byte[] getTemplate(String resourcePath) {
		byte[] cached = templateCache.get(resourcePath);
		if (cached != null) {
			templateHits.incrementAndGet();
			log.debug("Template cache HIT  {}", resourcePath);
		} else {
			templateMisses.incrementAndGet();
			log.debug("Template cache MISS {}", resourcePath);
		}
		return cached;
	}

	/** Stores template bytes in the cache. */
	public void putTemplate(String resourcePath, byte[] bytes) {
		if (bytes != null) {
			templateCache.put(resourcePath, bytes);
		}
	}

	/** Removes a single template entry so it is reloaded on the next request. */
	public void evictTemplate(String resourcePath) {
		templateCache.remove(resourcePath);
		log.info("Evicted template cache entry: {}", resourcePath);
	}

	// ------------------------------------------------------------------
	// Bulk operations
	// ------------------------------------------------------------------

	/** Clears both caches. The next request will reload everything from the classpath. */
	public void clearAll() {
		int artifacts = artifactCache.size();
		int templates = templateCache.size();
		artifactCache.clear();
		templateCache.clear();
		log.info("Generator cache cleared ({} artifacts, {} templates)", artifacts, templates);
	}

	/** Returns a snapshot of current cache statistics. */
	public CacheStats stats() {
		return new CacheStats(
				artifactCache.size(), artifactHits.get(), artifactMisses.get(),
				templateCache.size(), templateHits.get(), templateMisses.get(),
				artifactCache.keySet().stream().sorted().toList(),
				templateCache.keySet().stream().sorted().toList());
	}

	// ------------------------------------------------------------------
	// Stats DTO
	// ------------------------------------------------------------------

	public record CacheStats(
			int artifactEntries,
			long artifactHits,
			long artifactMisses,
			int templateEntries,
			long templateHits,
			long templateMisses,
			java.util.List<String> artifactKeys,
			java.util.List<String> templateKeys) {
	}

	/** Exposes the underlying artifact map for testing. */
	Map<String, byte[]> artifactCacheView() {
		return Map.copyOf(artifactCache);
	}

	/** Exposes the underlying template map for testing. */
	Map<String, byte[]> templateCacheView() {
		return Map.copyOf(templateCache);
	}
}
