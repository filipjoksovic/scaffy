package com.scaffy.backend.init.generator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for inspecting and invalidating the generator resource cache.
 *
 * <p>{@code DELETE /api/cache} — clear all cached artifacts and templates.
 * <p>{@code DELETE /api/cache?artifact=artifacts/spring-boot.zip} — evict one artifact.
 * <p>{@code DELETE /api/cache?template=templates/root/README.md.tmpl} — evict one template.
 * <p>{@code GET    /api/cache/stats} — current hit/miss counters and cached keys.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

	private final GeneratorCacheManager cacheManager;

	public CacheController(GeneratorCacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@GetMapping("/stats")
	public ResponseEntity<GeneratorCacheManager.CacheStats> stats() {
		return ResponseEntity.ok(cacheManager.stats());
	}

	@DeleteMapping
	public ResponseEntity<String> clear(
			@RequestParam(name = "artifact", required = false) String artifact,
			@RequestParam(name = "template", required = false) String template) {
		if (artifact != null) {
			cacheManager.evictArtifact(artifact);
			return ResponseEntity.ok("Evicted artifact: " + artifact);
		}
		if (template != null) {
			cacheManager.evictTemplate(template);
			return ResponseEntity.ok("Evicted template: " + template);
		}
		cacheManager.clearAll();
		return ResponseEntity.ok("Generator cache cleared");
	}
}
