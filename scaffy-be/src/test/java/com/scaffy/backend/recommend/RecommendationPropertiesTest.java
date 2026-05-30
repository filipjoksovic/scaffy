package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecommendationPropertiesTest {

	@Test
	void appliesDefaultsForOpenAi() {
		RecommendationProperties props = new RecommendationProperties(null, null);

		assertThat(props.provider()).isEqualTo("openai");
		assertThat(props.openai().model()).isEqualTo("gpt-4o-mini");
		assertThat(props.openai().temperature()).isEqualTo(0.2);
		assertThat(props.openai().baseUrl()).isEqualTo("https://api.openai.com/v1");
		assertThat(props.openai().apiKey()).isNull();
	}

	@Test
	void preservesProvidedValues() {
		RecommendationProperties props = new RecommendationProperties(
				"openai",
				new RecommendationProperties.OpenAi("sk-test", "gpt-5-mini", 0.0, "https://example.test/v1"));

		assertThat(props.provider()).isEqualTo("openai");
		assertThat(props.openai().model()).isEqualTo("gpt-5-mini");
		assertThat(props.openai().temperature()).isEqualTo(0.0);
		assertThat(props.openai().baseUrl()).isEqualTo("https://example.test/v1");
		assertThat(props.openai().apiKey()).isEqualTo("sk-test");
	}
}
