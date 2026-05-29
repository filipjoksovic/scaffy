package com.scaffy.backend.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = GitLabInstanceController.class)
public class GitLabInstanceExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
		String message = ex.getReason();
		if (message == null || message.isBlank()) {
			message = ex.getStatusCode().toString();
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", "GitLab instance request failed");
		body.put("message", message);
		return ResponseEntity.status(ex.getStatusCode()).body(body);
	}
}
