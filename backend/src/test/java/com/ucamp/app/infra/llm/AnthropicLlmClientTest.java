package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 API 대신 WireMock으로 HTTP 요청·응답 형식을 확인한다
class AnthropicLlmClientTest {
    private WireMockServer server;
    private AnthropicLlmClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        LlmProperties props = new LlmProperties();
        props.getAnthropic().setBaseUrl("http://localhost:" + server.port());
        props.setTimeoutMs(1000);
        client = new AnthropicLlmClient(props, objectMapper, () -> "test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void 텍스트_응답과_사용량_파싱() {
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withHeader("request-id", "req_1")
                .withBody("""
                        {"id":"msg_1","model":"claude-opus-5-5","stop_reason":"end_turn",
                         "content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\\"a\\":1}"}],
                         "usage":{"input_tokens":120,"output_tokens":30,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
                        """)));

        LlmResponse res = client.call(request(false));

        assertThat(res.text()).isEqualTo("{\"a\":1}");
        assertThat(res.usage().inputTokens()).isEqualTo(120);
        assertThat(res.usage().outputTokens()).isEqualTo(30);
        assertThat(res.requestId()).isEqualTo("req_1");
        assertThat(res.model()).isEqualTo("claude-opus-5-5");
        server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-key"))
                .withHeader("anthropic-version", equalTo("2023-06-01"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].text", equalTo("안녕")))
                .withRequestBody(matchingJsonPath("$.system[0].text", equalTo("시스템"))));
    }

    @Test
    void tool_use_입력_파싱과_캐시_지점_표시() {
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"msg_2","model":"claude-opus-5-5","stop_reason":"tool_use",
                         "content":[{"type":"tool_use","id":"tu_1","name":"submit_recommendation","input":{"summary":"s","recommendations":[]}}],
                         "usage":{"input_tokens":900,"output_tokens":40,"cache_read_input_tokens":700,"cache_creation_input_tokens":0}}
                        """)));

        LlmResponse res = client.call(request(true));

        assertThat(res.toolName()).isEqualTo("submit_recommendation");
        assertThat(res.toolInput().get("summary").asText()).isEqualTo("s");
        assertThat(res.usage().cacheReadTokens()).isEqualTo(700);
        // 강제 tool_choice는 최신 모델이 거부하므로 기본은 auto + strict 도구
        server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.tool_choice", equalToJson("{\"type\":\"auto\"}")))
                .withRequestBody(matchingJsonPath("$.tools[0].strict", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.tools[0].cache_control.type", equalTo("ephemeral")))
                .withRequestBody(matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral"))));
    }

    @Test
    void HTTP_429는_retry_after와_함께_예외() {
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(429)
                .withHeader("retry-after", "7")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"x\"}}")));

        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(LlmCallException.Kind.HTTP_ERROR);
                    assertThat(e.getHttpStatus()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(7L);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void HTTP_400은_재시도_대상이_아니다() {
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(400).withBody("{}")));

        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void 응답이_타임아웃보다_늦으면_TIMEOUT() {
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2000).withBody("{}")));

        assertThatThrownBy(() -> client.call(request(false)))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.getKind()).isEqualTo(LlmCallException.Kind.TIMEOUT));
    }

    @Test
    void API_키가_없으면_호출하지_않는다() {
        LlmProperties props = new LlmProperties();
        props.getAnthropic().setBaseUrl("http://localhost:" + server.port());
        AnthropicLlmClient noKey = new AnthropicLlmClient(props, objectMapper, () -> null);

        assertThatThrownBy(() -> noKey.call(request(false))).isInstanceOf(LlmCallException.class);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private LlmRequest request(boolean withTool) {
        LlmRequest.LlmRequestBuilder b = LlmRequest.builder()
                .model("claude-opus-5-5")
                .system("시스템")
                .messages(List.of(LlmMessage.user("안녕")))
                .maxTokens(100);
        if (withTool) {
            b.tools(List.of(new LlmTool("submit_recommendation", "추천 제출",
                            Map.of("type", "object", "properties", Map.of()), true)))
                    .toolName("submit_recommendation")
                    .cacheControl(true);
        }
        return b.build();
    }
}
