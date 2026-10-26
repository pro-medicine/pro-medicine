package com.ucamp.app.infra.llm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 API 대신 WireMock으로 Spring AI가 보내는 OpenAI 요청·응답 형식을 확인한다
class SpringAiLlmClientTest {
    private WireMockServer server;
    private SpringAiLlmClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = client(2000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void 텍스트_응답과_사용량_파싱() {
        server.stubFor(OpenAiStubs.ok("{\"a\":1}"));

        LlmResponse res = client.call(request(false));

        assertThat(res.text()).isEqualTo("{\"a\":1}");
        assertThat(res.output()).isNull();
        assertThat(res.usage().inputTokens()).isEqualTo(120);
        assertThat(res.usage().outputTokens()).isEqualTo(30);
        assertThat(res.requestId()).isEqualTo("chatcmpl-test");
        assertThat(res.model()).isEqualTo("gpt-6.1-sol");
        assertThat(res.stopReason()).isEqualToIgnoringCase("stop");
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6.1-sol")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.max_completion_tokens", equalTo("100")))
                // 최신 추론 모델은 temperature를 거부할 수 있어 요청에 없을 때 보내지 않는다
                .withRequestBody(matchingJsonPath("$.temperature", absent()))
                .withRequestBody(matchingJsonPath("$.max_tokens", absent())));
    }

    @Test
    void 출력_스키마는_structured_outputs로_보내고_응답_JSON을_output으로_돌려준다() {
        server.stubFor(OpenAiStubs.ok("{\"recommendations\":[{\"ingredient\":\"VITAMIN_D\"}],\"summary\":\"s\"}", 1500, 300, 1024));

        LlmResponse res = client.call(request(true).toBuilder().promptCache(true).build());

        assertThat(res.output().path("recommendations").get(0).path("ingredient").asString()).isEqualTo("VITAMIN_D");
        assertThat(res.text()).isEmpty();
        // prompt_tokens(1500)는 캐시 토큰(1024)을 포함하므로 캐시 제외분만 inputTokens로 센다
        assertThat(res.usage().inputTokens()).isEqualTo(476);
        assertThat(res.usage().cacheReadTokens()).isEqualTo(1024);
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.response_format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.strict", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.schema.required[0]", equalTo("recommendations")))
                .withRequestBody(matchingJsonPath("$.prompt_cache_key", equalTo("V1:v1")))
                .withRequestBody(matchingJsonPath("$.tools", absent())));
    }

    @Test
    void 요청별_추론_강도는_reasoning_effort로_보내고_없으면_보내지_않는다() {
        server.stubFor(OpenAiStubs.ok("{\"question\":\"q\"}"));

        client.call(request(true).toBuilder().reasoningEffort("low").build());
        client.call(request(true));

        server.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("low"))));
        server.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", absent())));
    }

    @Test
    void 형식이_깨진_응답은_output_없이_돌려준다() {
        server.stubFor(OpenAiStubs.ok("not json"));

        LlmResponse res = client.call(request(true));

        assertThat(res.output()).isNull();
    }

    @Test
    void 상태_429는_retry_after와_함께_재시도_가능한_오류로_바꾼다() {
        server.stubFor(OpenAiStubs.error(429, "rate_limit_exceeded", "3"));

        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(3L);
                    assertThat(e.isRetryable()).isTrue();
                });
        // SDK 자체 재시도는 끈다 (재시도는 ResilientLlmClient 담당)
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void 상태_500은_재시도_가능_400은_재시도_불가() {
        server.stubFor(OpenAiStubs.error(500, "server_error", null));
        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(500);
                    assertThat(e.isRetryable()).isTrue();
                });

        server.resetAll();
        server.stubFor(OpenAiStubs.error(400, "invalid_request_error", null));
        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                });
    }

    @Test
    void 응답이_타임아웃보다_늦으면_TIMEOUT() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(OpenAiStubs.okResponse("{}", 1, 1, 0).withFixedDelay(1500)));
        SpringAiLlmClient slowClient = client(300);

        assertThatThrownBy(() -> slowClient.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.getKind()).isEqualTo(LlmCallException.Kind.TIMEOUT));
    }

    // 자동 설정(spring.ai.openai.*)이 만드는 것과 같은 옵션으로 ChatModel을 직접 만든다 (SpringAiWiringTest가 자동 설정 경로를 확인)
    private SpringAiLlmClient client(int timeoutMs) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + server.port() + "/v1")
                        .timeout(Duration.ofMillis(timeoutMs))
                        .maxRetries(0)
                        .build())
                .build();
        return new SpringAiLlmClient(chatModel, new OpenAiProviderSupport(), new LlmProperties(), objectMapper);
    }

    private LlmRequest request(boolean withSchema) {
        LlmRequest.LlmRequestBuilder b = LlmRequest.builder()
                .model("gpt-6.1-sol")
                .system("시스템")
                .messages(List.of(LlmMessage.user("안녕")))
                .maxTokens(100)
                .pipelineVersion("V1")
                .promptVersion("v1");
        if (withSchema) {
            b.outputSchema(new LlmOutputSchema("submit_recommendation", Map.of(
                    "type", "object",
                    "properties", Map.of("recommendations", Map.of("type", "array", "items", Map.of("type", "object")),
                            "summary", Map.of("type", "string")),
                    "required", List.of("recommendations", "summary"),
                    "additionalProperties", false), true));
        }
        return b.build();
    }
}
