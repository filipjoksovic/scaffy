package com.scaffy.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.scaffy.backend.auth.ScaffyPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryConnectionController {

	private final RepositoryConnectionRepository repository;
	private final GitHubRepositoryClient gitHubRepositoryClient;
	private final GitHubRepositoryRefParser parser;

	public RepositoryConnectionController(
			RepositoryConnectionRepository repository,
			GitHubRepositoryClient gitHubRepositoryClient,
			GitHubRepositoryRefParser parser) {
		this.repository = repository;
		this.gitHubRepositoryClient = gitHubRepositoryClient;
		this.parser = parser;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RepositoryConnectionResponse> list(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return repository.findByUserId(principal.userId())
				.stream()
				.map(RepositoryConnectionResponse::from)
				.toList();
	}

	@GetMapping(path = "/github", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<GitHubRepositoryResponse> listGitHubRepositories(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return gitHubRepositoryClient.findRepositories(principal.userId())
				.stream()
				.map(GitHubRepositoryResponse::from)
				.toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public RepositoryConnectionResponse connect(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@Valid @RequestBody ConnectRepositoryRequest request) {
		GitHubRepositoryRef ref = parser.parse(request.repository());
		return RepositoryConnectionResponse.from(repository.connectGitHub(principal.userId(), ref));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(@AuthenticationPrincipal ScaffyPrincipal principal, @PathVariable UUID id) {
		if (!repository.deleteForUser(principal.userId(), id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository connection not found.");
		}
	}

	public record RepositoryConnectionResponse(
			String id,
			String provider,
			String owner,
			String name,
			String url,
			OffsetDateTime connectedAt) {

		static RepositoryConnectionResponse from(RepositoryConnection connection) {
			return new RepositoryConnectionResponse(
					connection.id().toString(),
					connection.provider(),
					connection.owner(),
					connection.name(),
					connection.url(),
					connection.connectedAt());
		}
	}

	public record GitHubRepositoryResponse(
			String fullName,
			String owner,
			String name,
			String url,
			boolean privateRepository) {

		static GitHubRepositoryResponse from(GitHubRepositoryOption repository) {
			return new GitHubRepositoryResponse(
					repository.fullName(),
					repository.owner(),
					repository.name(),
					repository.url(),
					repository.privateRepository());
		}
	}
}
