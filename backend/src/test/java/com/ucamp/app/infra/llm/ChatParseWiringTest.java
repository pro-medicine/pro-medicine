package com.ucamp.app.infra.llm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ucamp.app.domain.chat.parse.ChatParser;
import com.ucamp.app.infra.llm.embed.EmbeddingClient;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.GoldenFiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4 대화창 LLM 구조화·임베딩이 Spring AI 자동 설정 경로로 나가는 모양 확인 (실제 API 대신 WireMock, 비용 없음).
 * 골든 openai/chat-parse-request = OpenAI로 나간 구조화 요청 본문 전체 (프롬프트 chat-v2 + 스키마)
 */
@SpringBootTest
class ChatParseWiringTest {
    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.ai.model.chat", () -> "openai");
        r.add("spring.ai.model.embedding", () -> "openai");
        r.add("spring.ai.openai.embedding.options.model", () -> "text-embedding-3-small");
        r.add("llm.provider", () -> "spring-ai");
        r.add("OPENAI_API_KEY", () -> "key-from-dotenv");
        r.add("spring.ai.openai.base-url", () -> "http://localhost:" + WIREMOCK.port() + "/v1");
    }

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    @Autowired
    ChatParser parser;
    @Autowired
    EmbeddingClient embeddingClient;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LlmCallLogRepository logRepository;

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
    }

    @Test
    void 대화_구조화_요청은_chat_v1_프롬프트와_strict_스키마로_나간다() throws Exception {
        WIREMOCK.stubFor(OpenAiStubs.ok(objectMapper.writeValueAsString(Map.of(
                "red_flag", "NONE", "off_topic", false, "needs_clarification", false,
                "clarification", Map.of(), "search_query", "야근으로 피로가 쌓이고 잠들기 어려운 사람에게 추천",
                "medications", List.of(Map.of("name", "와파린", "drug_class", "WARFARIN")),
                "conditions", List.of()))
                .replace("\"clarification\":{}", "\"clarification\":null")));

        ChatParser.Parsed parsed = parser.parse("요즘 야근이 많아서 피곤하고 잠을 못 자요. 와파린 먹어요", null, null);

        assertThat(parsed.parse().searchQuery()).isEqualTo("야근으로 피로가 쌓이고 잠들기 어려운 사람에게 추천");
        assertThat(parsed.parse().medications()).hasSize(1);
        List<LoggedRequest> sent = WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")));
        assertThat(sent).hasSize(1);
        JsonNode body = objectMapper.readTree(sent.get(0).getBodyAsString());
        GoldenFiles.assertMatches("openai/chat-parse-request", body);
        JsonNode format = body.path("response_format").path("json_schema");
        assertThat(format.path("strict").asBoolean()).isTrue();
        String system = body.path("messages").get(0).path("content").asString();
        // drug_class 후보는 drugs 테이블에서 (하드코딩 아님)
        assertThat(system).contains("ANTICOAGULANT_UNKNOWN: 항응고제 (이름 모름)").contains("WARFARIN: 와파린");
        LlmCallLog log = logRepository.findById(parsed.logIds().get(0)).orElseThrow();
        assertThat(log.getCallTag()).isEqualTo(ChatParser.TAG_CHAT_PARSE);
        assertThat(log.getPromptVersion()).isEqualTo("chat-v2");
    }

    @Test
    void 임베딩은_설정한_모델로_나가고_CHAT_EMBED로_기록된다() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/embeddings")).willReturn(WireMock.okJson("""
                {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.6,0.8,0.0]}],
                 "model":"text-embedding-3-small","usage":{"prompt_tokens":9,"total_tokens":9}}""")));

        EmbeddingClient.Embedding e = embeddingClient.embed("피로가 쌓이고 잠들기 어려운 사람에게 추천");

        assertThat(e.vector()).containsExactly(0.6f, 0.8f, 0.0f);
        JsonNode body = objectMapper.readTree(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/embeddings")))
                .get(0).getBodyAsString());
        assertThat(body.path("model").asString()).isEqualTo("text-embedding-3-small");
        LlmCallLog log = logRepository.findById(e.callLogId()).orElseThrow();
        assertThat(log.getCallTag()).isEqualTo(EmbeddingClient.TAG_CHAT_EMBED);
        assertThat(log.getInputTokens()).isEqualTo(9);
        assertThat(log.getOutputTokens()).isNull();
        assertThat(log.getModel()).isEqualTo("text-embedding-3-small");
    }
}
