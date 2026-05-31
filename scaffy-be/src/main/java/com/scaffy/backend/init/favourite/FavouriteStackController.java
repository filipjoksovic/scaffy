package com.scaffy.backend.init.favourite;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.ScaffyPrincipal;

import jakarta.validation.Valid;

/**
 * REST endpoints for managing a user's favourite stack presets.
 *
 * <p>All routes require an authenticated principal — Spring Security rejects
 * unauthenticated requests before they reach this controller.
 *
 * <p>{@code GET    /api/init/favourites}       — list the user's saved presets
 * <p>{@code POST   /api/init/favourites}       — save a new preset
 * <p>{@code DELETE /api/init/favourites/{id}}  — remove a preset
 */
@RestController
@RequestMapping("/api/init/favourites")
public class FavouriteStackController {

	private final FavouriteStackRepository repository;

	public FavouriteStackController(FavouriteStackRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<FavouriteStack> list(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return repository.findByUserId(principal.userId());
	}

	@PostMapping
	public ResponseEntity<FavouriteStack> save(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@Valid @RequestBody FavouriteStackRequest request) {
		if (repository.countByUserId(principal.userId()) >= repository.maxPerUser()) {
			throw new ResponseStatusException(HttpStatusCode.valueOf(422),
					"You have reached the limit of " + repository.maxPerUser() + " favourite stacks. "
							+ "Remove one before saving a new preset.");
		}

		FavouriteStack favourite = new FavouriteStack(
				UUID.randomUUID(),
				principal.userId(),
				request.name(),
				request.frontend(),
				request.frontendVersion(),
				request.frontendRuntime(),
				request.backend(),
				request.backendVersion(),
				request.backendRuntime(),
				request.pipeline(),
				request.pipelineMaturity(),
				request.includeDocker(),
				OffsetDateTime.now(ZoneOffset.UTC));

		return ResponseEntity.status(201).body(repository.save(favourite));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		boolean deleted = repository.deleteByIdAndUserId(id, principal.userId());
		if (!deleted) {
			throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Favourite stack not found.");
		}
		return ResponseEntity.noContent().build();
	}
}
