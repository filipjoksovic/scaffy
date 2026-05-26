package com.scaffy.backend.repository;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = RepositoryConnectionController.class)
public class RepositoryConnectionExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
		String message = ex.getReason();
		if (message == null || message.isBlank()) {
			message = ex.getStatusCode().toString();
		}
		return ResponseEntity.status(ex.getStatusCode()).body(body("Repository request failed", message));
	}

	private Map<String, Object> body(String error, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		return body;
	}
}
