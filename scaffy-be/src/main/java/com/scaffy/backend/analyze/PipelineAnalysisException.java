package com.scaffy.backend.analyze;

public class PipelineAnalysisException extends RuntimeException {

	private final String error;

	public PipelineAnalysisException(String error, String message) {
		super(message);
		this.error = error;
	}

	public String error() {
		return error;
	}

	public static PipelineAnalysisException invalidUpload(String message) {
		return new PipelineAnalysisException("Invalid pipeline upload", message);
	}
}
