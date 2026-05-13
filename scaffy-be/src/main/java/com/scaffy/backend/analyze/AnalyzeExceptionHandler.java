package com.scaffy.backend.analyze;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = AnalyzeController.class)
public class AnalyzeExceptionHandler {

	@ExceptionHandler(PipelineAnalysisException.class)
	public ResponseEntity<Map<String, Object>> handlePipelineAnalysis(PipelineAnalysisException ex) {
		return ResponseEntity.badRequest().body(body(ex.error(), ex.getMessage()));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
		return ResponseEntity.badRequest()
				.body(body("Invalid pipeline upload", "Multipart field 'file' is required."));
	}

	private Map<String, Object> body(String error, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		return body;
	}
}
