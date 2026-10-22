package com.ucamp.app.infra.llm;

import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 OpenAI API 호출 (비용 발생). ./gradlew liveTest 로만 실행된다 (기본 test에서 제외).
 * 키는 backend/.env 또는 환경변수의 OPENAI_API_KEY (Gradle이 테스트 JVM 환경변수로 넘긴다).
 * 호출 수: 연결 확인 1 + 추천 전체 흐름 1~2(형식 실패 시 재요청) = 최대 3.
 * 결과(요청 ID·토큰·응답)는 build/reports/llm-integration/live-*.json 에 남긴다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class SpringAiLlmClientLiveTest {
    static final Path REPORT_DIR = Path.of("build", "reports", "llm-integration");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.ai.model.chat", () -> "openai");
        r.add("llm.provider", () -> "spring-ai");
        r.add("recommendation.pipeline-version", () -> "V4");
        String model = System.getProperty("live.model");
        if (model != null && !model.isBlank()) {
            r.add("llm.model", () -> model);
        }
    }

    @Autowired
    LlmClient llmClient;
    @Autowired
    LlmProperties properties;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LlmCallLogRepository logRepository;

    @Test
    void 연결과_구조화_출력_1회() throws Exception {
        LlmResponse res = llmClient.call(LlmRequest.builder()
                .model(properties.getModel())
                .system("You are a test endpoint. Reply only with the requested JSON.")
                .messages(List.of(LlmMessage.user("Return status ok and the number 2.")))
                .maxTokens(2000)
                .tools(List.of(new LlmTool("live_check", "connectivity check", Map.of(
                        "type", "object",
                        "properties", Map.of("status", Map.of("type", "string"), "number", Map.of("type", "integer")),
                        "required", List.of("status", "number"),
                        "additionalProperties", false), true)))
                .toolName("live_check")
                .callTag("LIVE_CHECK")
                .build());

        System.out.printf("[LIVE] model=%s id=%s stop=%s usage=%s toolInput=%s latencyMs=%d%n", res.model(),
                res.requestId(), res.stopReason(), res.usage(), res.toolInput(), res.latencyMs());
        write("live-connectivity.json", Map.of("model", String.valueOf(res.model()), "requestId", String.valueOf(res.requestId()),
                "stopReason", String.valueOf(res.stopReason()), "usage", res.usage(), "latencyMs", res.latencyMs(),
                "toolInput", res.toolInput() == null ? "null" : res.toolInput()));
        assertThat(res.toolInput()).isNotNull();
        assertThat(res.toolInput().path("status").isString()).isTrue();
        assertThat(res.toolInput().path("number").asInt()).isEqualTo(2);
        assertThat(res.usage().outputTokens()).isPositive();
    }

    @Test
    void 설문부터_추천까지_실제_모델로_1회() throws Exception {
        String survey = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                                choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995),
                                choice(Q_SMOKING, SMOKING_ONE_PACK), choice(Q_SUN, SUN_LOW))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(survey).get("data").get("responseId").asLong();

        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        List<LlmCallLog> logs = logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId);

        System.out.println("[LIVE] recommendation=" + data);
        logs.forEach(l -> System.out.printf("[LIVE] log tag=%s model=%s in=%s cacheRead=%s out=%s latencyMs=%s outcome=%s%n",
                l.getCallTag(), l.getModel(), l.getInputTokens(), l.getCacheReadTokens(), l.getOutputTokens(),
                l.getLatencyMs(), l.getOutcome()));
        write("live-recommendation.json", Map.of("apiResponse", data, "calls", logs.stream().map(l -> Map.of(
                "callTag", l.getCallTag(), "model", String.valueOf(l.getModel()),
                "inputTokens", String.valueOf(l.getInputTokens()), "cacheReadTokens", String.valueOf(l.getCacheReadTokens()),
                "outputTokens", String.valueOf(l.getOutputTokens()), "latencyMs", String.valueOf(l.getLatencyMs()),
                "outcome", String.valueOf(l.getOutcome()))).toList()));

        assertThat(data.path("status").asString()).isEqualTo("DONE");
        assertThat(data.path("fallback").asBoolean()).isFalse();
        assertThat(data.path("items").size()).isPositive();
        // 흡연자 규칙 R-02: 베타카로틴은 최종 추천에 없어야 한다
        assertThat(data.path("items").toString()).doesNotContain("BETA_CAROTENE");
        assertThat(logs).isNotEmpty().hasSizeLessThanOrEqualTo(2);
    }

    private void write(String name, Object value) throws Exception {
        Files.createDirectories(REPORT_DIR);
        Files.writeString(REPORT_DIR.resolve(name), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
