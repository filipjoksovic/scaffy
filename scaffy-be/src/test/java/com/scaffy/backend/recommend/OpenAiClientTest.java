package com.scaffy.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiClientTest {

	private static final String BASE_URL = "https://example.test/v1";

	@Test
	void isAvailableReturnsFalseWhenApiKeyIsMissing() {
		OpenAiClient client = new OpenAiClient(properties(null));

		assertThat(client.isAvailable()).isFalse();
		assertThat(client.model()).isEqualTo("gpt-4o-mini");
	}

	@Test
	void isAvailableReturnsTrueWhenApiKeyIsPresent() {
		OpenAiClient client = new OpenAiClient(properties("sk-present"));

		assertThat(client.isAvailable()).isTrue();
	}

	@Test
	void completeThrowsWhenApiKeyIsMissing() {
		OpenAiClient client = new OpenAiClient(properties(null));

		assertThatThrownBy(() -> client.complete("sys", "user"))
				.isInstanceOf(LlmCallException.class)
				.hasMessageContaining("not configured");
	}

	@Test
	void completeReturnsAssistantMessageContent() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		String successBody = """
				{
				  "choices": [
				    {
				      "message": {
				        "role": "assistant",
				        "content": "{\\"recommendations\\":[]}"
				      }
				    }
				  ]
				}
				""";
		server.expect(requestTo(BASE_URL + "/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer sk-test"))
				.andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

		OpenAiClient client = new OpenAiClient(properties("sk-test"), builder);

		String content = client.complete("sys", "user");

		assertThat(content).isEqualTo("{\"recommendations\":[]}");
		server.verify();
	}

	@Test
	void completeThrowsLlmCallExceptionOnNon200Status() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		server.expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(withRawStatus(500).body("upstream broke"));

		OpenAiClient client = new OpenAiClient(properties("sk-test"), builder);

		assertThatThrownBy(() -> client.complete("sys", "user"))
				.isInstanceOf(LlmCallException.class);
	}

	@Test
	void completeThrowsWhenResponseHasNoChoices() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		server.expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

		OpenAiClient client = new OpenAiClient(properties("sk-test"), builder);

		assertThatThrownBy(() -> client.complete("sys", "user"))
				.isInstanceOf(LlmCallException.class)
				.hasMessageContaining("choices");
	}

	@Test
	void completeThrowsWhenMessageContentIsBlank() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		String body = """
				{ "choices": [ { "message": { "content": "" } } ] }
				""";
		server.expect(requestTo(BASE_URL + "/chat/completions"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		OpenAiClient client = new OpenAiClient(properties("sk-test"), builder);

		assertThatThrownBy(() -> client.complete("sys", "user"))
				.isInstanceOf(LlmCallException.class)
				.hasMessageContaining("content");
	}

	private RecommendationProperties properties(String apiKey) {
		return new RecommendationProperties(
				"openai",
				new RecommendationProperties.OpenAi(apiKey, "gpt-4o-mini", 0.2, BASE_URL));
	}
}
