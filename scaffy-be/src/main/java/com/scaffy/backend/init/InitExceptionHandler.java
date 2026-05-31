package com.scaffy.backend.init;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InitController.class)
public class InitExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(body("Invalid request", message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(body("Malformed JSON", "Request body could not be parsed."));
	}

	@ExceptionHandler(UnsupportedStackException.class)
	public ResponseEntity<Map<String, Object>> handleUnsupported(UnsupportedStackException ex) {
		return ResponseEntity.badRequest().body(body("Unsupported stack combination", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(body("Project generation failed", ex.getMessage()));
	}

	@ExceptionHandler(InitJobNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleJobNotFound(InitJobNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(body("Initializer job not found", ex.getMessage()));
	}

	@ExceptionHandler(InitJobUnavailableException.class)
	public ResponseEntity<Map<String, Object>> handleJobUnavailable(InitJobUnavailableException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(body("Initializer job unavailable", ex.getMessage()));
	}

	@ExceptionHandler(InitJobLimitExceededException.class)
	public ResponseEntity<Map<String, Object>> handleJobLimit(InitJobLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(body("Too many concurrent jobs", ex.getMessage()));
	}

	private Map<String, Object> body(String error, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		return body;
	}
}
