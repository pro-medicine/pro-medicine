package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.Mockito.mock;

// API 키를 Spring Environment(OS 환경변수 또는 backend/.env import)에서 읽는지 확인한다
class LlmConfigApiKeyTest {
    private WireMockServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"msg_1","model":"claude-opus-5-5","stop_reason":"end_turn",
                         "content":[{"type":"text","text":"ok"}],
                         "usage":{"input_tokens":1,"output_tokens":1}}
                        """)));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void Environment의_ANTHROPIC_API_KEY를_헤더로_보낸다() {
        LlmProperties props = new LlmProperties();
        props.setProvider("anthropic");
        props.setTimeoutMs(1000);
        props.getAnthropic().setBaseUrl("http://localhost:" + server.port());
        MockEnvironment environment = new MockEnvironment().withProperty("ANTHROPIC_API_KEY", "key-from-dotenv");
        StaticListableBeanFactory none = new StaticListableBeanFactory();

        LlmClient client = new LlmConfig().llmClient(props, objectMapper, new FakeLlmClient(objectMapper),
                mock(LlmCallLogService.class), none.getBeanProvider(io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.class),
                none.getBeanProvider(io.github.resilience4j.retry.RetryRegistry.class),
                none.getBeanProvider(io.github.resilience4j.bulkhead.BulkheadRegistry.class),
                new SimpleMeterRegistry(), environment);
        client.call(LlmRequest.builder().model("claude-opus-5-5").system("시스템")
                .messages(List.of(LlmMessage.user("안녕"))).maxTokens(10).build());

        server.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("key-from-dotenv")));
    }
}
