package com.scaffy.backend.recommend;

public interface LlmClient {

	boolean isAvailable();

	String model();

	String complete(String systemPrompt, String userPrompt);
}
