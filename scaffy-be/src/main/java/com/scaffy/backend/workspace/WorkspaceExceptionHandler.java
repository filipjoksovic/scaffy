package com.scaffy.backend.workspace;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = WorkspaceController.class)
public class WorkspaceExceptionHandler {

	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateKeyException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(body("Workspace request failed", "That value is already in use."));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
		String message = ex.getReason();
		if (message == null || message.isBlank()) {
			message = ex.getStatusCode().toString();
		}
		return ResponseEntity.status(ex.getStatusCode()).body(body("Workspace request failed", message));
	}

	private Map<String, Object> body(String error, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		return body;
	}
}
