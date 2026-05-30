package com.scaffy.backend.recommend;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.recommend")
public record RecommendationProperties(
		String provider,
		OpenAi openai) {

	public RecommendationProperties {
		if (provider == null || provider.isBlank()) {
			provider = "openai";
		}
		if (openai == null) {
			openai = new OpenAi(null, null, null, null);
		}
	}

	public record OpenAi(
			String apiKey,
			String model,
			Double temperature,
			String baseUrl) {

		public OpenAi {
			if (model == null || model.isBlank()) {
				model = "gpt-4o-mini";
			}
			if (temperature == null) {
				temperature = 0.2;
			}
			if (baseUrl == null || baseUrl.isBlank()) {
				baseUrl = "https://api.openai.com/v1";
			}
		}
	}
}
