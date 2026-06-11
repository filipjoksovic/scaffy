package com.scaffy.backend.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.auth.ScaffyPrincipal;

@RestController
@RequestMapping("/api/notifications")
public class AppNotificationController {

	private final AppNotificationRepository repository;

	public AppNotificationController(AppNotificationRepository repository) {
		this.repository = repository;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<AppNotificationResponse> list(@AuthenticationPrincipal ScaffyPrincipal principal) {
		return repository.listUnread(principal.userId()).stream()
				.map(AppNotificationResponse::from)
				.toList();
	}

	@PostMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(
			@AuthenticationPrincipal ScaffyPrincipal principal,
			@PathVariable UUID id) {
		repository.markRead(principal.userId(), id);
	}

	public record AppNotificationResponse(
			String id,
			String workspaceId,
			String type,
			String title,
			String message,
			String targetUrl,
			OffsetDateTime createdAt) {

		static AppNotificationResponse from(AppNotification notification) {
			return new AppNotificationResponse(
					notification.id().toString(),
					notification.workspaceId() == null ? null : notification.workspaceId().toString(),
					notification.type(),
					notification.title(),
					notification.message(),
					notification.targetUrl(),
					notification.createdAt());
		}
	}
}
