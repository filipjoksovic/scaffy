package com.scaffy.backend.init;

public class InitJobLimitExceededException extends RuntimeException {

	public InitJobLimitExceededException(String message) {
		super(message);
	}
}
