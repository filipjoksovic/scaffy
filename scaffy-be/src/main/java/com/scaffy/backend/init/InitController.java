package com.scaffy.backend.init;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.auth.ScaffyPrincipal;
import com.scaffy.backend.init.generator.ProjectGenerator;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/init")
public class InitController {

	private final StackValidator stackValidator;
	private final ProjectGenerator projectGenerator;
	private final StackCatalog stackCatalog;
	private final InitJobService initJobService;
	private final InitArtifactStorage artifactStorage;

	public InitController(
			StackValidator stackValidator,
			ProjectGenerator projectGenerator,
			StackCatalog stackCatalog,
			InitJobService initJobService,
			InitArtifactStorage artifactStorage) {
		this.stackValidator = stackValidator;
		this.projectGenerator = projectGenerator;
		this.stackCatalog = stackCatalog;
		this.initJobService = initJobService;
		this.artifactStorage = artifactStorage;
	}

	@GetMapping("/catalog")
	public InitCatalogResponse catalog() {
		return stackCatalog.response();
	}

	@PostMapping(path = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<InitJobResponse> createJob(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody InitJobRequest request) {
		return ResponseEntity.accepted().body(initJobService.create(
				request,
				principal == null ? null : principal.userId(),
				idempotencyKey));
	}

	@GetMapping("/jobs/{jobId}")
	public InitJobResponse getJob(@PathVariable UUID jobId) {
		return initJobService.get(jobId);
	}

	@GetMapping("/history")
	public List<InitHistoryItem> history(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@RequestParam(defaultValue = "5") int limit) {
		return initJobService.history(principal.userId(), limit);
	}

	@GetMapping("/jobs/{jobId}/download")
	public ResponseEntity<byte[]> downloadJob(@PathVariable UUID jobId) {
		InitGenerationJob job = initJobService.getJob(jobId);
		if (!"succeeded".equals(job.status()) || job.artifactObjectKey() == null) {
			throw new InitJobUnavailableException("Initializer job is not ready for download.");
		}

		InitArtifact artifact = artifactStorage.download(job.artifactObjectKey());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header("Content-Disposition", "attachment; filename=\"" + artifact.filename() + "\"")
				.body(artifact.bytes());
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> init(@Valid @RequestBody InitRequest request) throws IOException {
		stackValidator.validate(request);
		byte[] zip = projectGenerator.generate(request);

		String filename = request.projectName() + ".zip";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(zip);
	}
}
