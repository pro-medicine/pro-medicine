package com.ucamp.app.infra.llm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
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

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring AI 자동 설정 경로 전체 확인 (실제 API 대신 WireMock, 비용 없음):
 * 설문 제출 → 입력 조립 → V4(규칙 사전 필터) → SpringAiLlmClient → Spring AI OpenAiChatModel → HTTP
 * → 구조화 출력 파싱 → 검증·재요청 → 규칙 사후 검증 → 용량 계산 → 저장 → API 응답.
 * 키는 local-llm 프로필처럼 ${OPENAI_API_KEY}(= backend/.env 값)로 들어간다.
 * 주고받은 본문은 build/reports/llm-integration/ 에 남겨 연동 보고서에 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpringAiWiringTest {
    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final Path REPORT_DIR = Path.of("build", "reports", "llm-integration");

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.ai.model.chat", () -> "openai");
        r.add("llm.provider", () -> "spring-ai");
        r.add("OPENAI_API_KEY", () -> "key-from-dotenv");
        r.add("spring.ai.openai.base-url", () -> "http://localhost:" + WIREMOCK.port() + "/v1");
        r.add("recommendation.pipeline-version", () -> "V4");
    }

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ChatModel chatModel;
    @Autowired
    LlmCallLogRepository logRepository;

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
    }

    @Test
    void 자동_설정이_OpenAiChatModel을_만들고_LlmClient가_그것을_쓴다() {
        assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void 설문부터_API_응답까지_LLM_앞뒤_데이터와_형식() throws Exception {
        // 흡연자: 규칙 R-02(베타카로틴 제외). LLM이 금지 목록을 무시하고 베타카로틴을 넣어도 사후 검증이 걸러야 한다
        WIREMOCK.stubFor(OpenAiStubs.ok(objectMapper.writeValueAsString(Map.of(
                "recommendations", List.of(
                        Map.of("ingredient", "VITAMIN_D", "reason", "햇빛 노출이 적어요", "priority", 1),
                        Map.of("ingredient", "BETA_CAROTENE", "reason", "항산화", "priority", 2)),
                "summary", "WireMock 응답")), 1500, 200, 1024));

        long responseId = submit(List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995),
                choice(Q_SMOKING, SMOKING_ONE_PACK), choice(Q_SUN, SUN_LOW)));
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");

        // LLM 앞: Spring AI가 보낸 OpenAI 요청
        List<LoggedRequest> sent = WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")));
        assertThat(sent).hasSize(1);
        LoggedRequest req = sent.get(0);
        JsonNode reqBody = objectMapper.readTree(req.getBodyAsString());
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer key-from-dotenv");
        assertThat(reqBody.path("model").asString()).isEqualTo("gpt-6.1-sol");
        assertThat(reqBody.path("max_completion_tokens").asInt()).isEqualTo(2000);
        assertThat(reqBody.has("temperature")).isFalse();
        assertThat(reqBody.has("tools")).isFalse();
        assertThat(reqBody.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(reqBody.path("messages").get(1).path("role").asString()).isEqualTo("user");
        String userText = reqBody.path("messages").get(1).path("content").asString();
        assertThat(userText).contains("BETA_CAROTENE"); // V4 사전 금지 목록
        JsonNode format = reqBody.path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("required").toString()).contains("recommendations");

        // LLM 뒤: API 응답 (규칙 사후 검증 + 용량 계산 + 저장)
        assertThat(data.path("status").asString()).isEqualTo("DONE");
        assertThat(data.path("modelName").asString()).isEqualTo("gpt-6.1-sol");
        assertThat(data.path("fallback").asBoolean()).isFalse();
        assertThat(data.path("items")).hasSize(1);
        assertThat(data.path("items").get(0).path("ingredient").asString()).isEqualTo("VITAMIN_D");
        assertThat(data.path("excludedItems").toString()).contains("R-02");

        // 호출 기록: 캐시 토큰을 뺀 입력 토큰, 캐시 읽기, 출력 토큰, 요청 ID
        List<LlmCallLog> logs = logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId);
        assertThat(logs).hasSize(1);
        LlmCallLog log = logs.get(0);
        assertThat(log.getInputTokens()).isEqualTo(476);
        assertThat(log.getCacheReadTokens()).isEqualTo(1024);
        assertThat(log.getOutputTokens()).isEqualTo(200);
        assertThat(log.getModel()).isEqualTo("gpt-6.1-sol");

        // DB에서 다시 조회해도 같은 형식
        mockMvc.perform(get("/api/v1/recommendations/" + data.path("recommendationId").asLong()))
                .andExpect(status().isOk());

        writeReport("normal", reqBody, body);
    }

    @Test
    void 형식이_깨진_응답은_재요청으로_복구한다() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/chat/completions")).inScenario("repair")
                .whenScenarioStateIs(Scenario.STARTED).willSetStateTo("second")
                .willReturn(OpenAiStubs.okResponse("{\"recommendations\": [", 1200, 50, 0)));
        WIREMOCK.stubFor(post(urlEqualTo("/v1/chat/completions")).inScenario("repair")
                .whenScenarioStateIs("second")
                .willReturn(OpenAiStubs.okResponse(objectMapper.writeValueAsString(Map.of(
                        "recommendations", List.of(Map.of("ingredient", "VITAMIN_D", "reason", "재요청 응답", "priority", 1)),
                        "summary", "재요청")), 1300, 80, 0)));

        long responseId = submit(List.of(choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1990)));
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")))).hasSize(2);
        assertThat(objectMapper.readTree(body).path("data").path("items").get(0).path("ingredient").asString())
                .isEqualTo("VITAMIN_D");
        List<LlmCallLog> logs = logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId);
        assertThat(logs).extracting(LlmCallLog::getCallTag)
                .containsExactly(LlmRequest.TAG_RECOMMEND, LlmRequest.TAG_RECOMMEND_REPAIR);
        writeReport("repair", objectMapper.readTree(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")))
                .get(1).getBodyAsString()), body);
    }

    @Test
    void LLM_오류는_기존_오류_형식으로_응답한다() throws Exception {
        WIREMOCK.stubFor(OpenAiStubs.error(500, "server_error", null));

        long responseId = submit(List.of(choice(Q_GENDER, GENDER_M)));
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andReturn().getResponse().getContentAsString();

        // SDK 자체 재시도는 꺼져 있어 1번만 호출된다 (spring.ai.openai.max-retries=0)
        assertThat(WIREMOCK.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")))).hasSize(1);
        assertThat(logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId))
                .extracting(LlmCallLog::getHttpStatus).containsExactly(500);
        writeReport("error-500", null, body);
    }

    private long submit(List<Map<String, Object>> answers) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("responseId").asLong();
    }

    private void writeReport(String name, JsonNode llmRequest, String apiResponse) throws Exception {
        Files.createDirectories(REPORT_DIR);
        Files.writeString(REPORT_DIR.resolve(name + "-llm-request.json"),
                llmRequest == null ? "{}" : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(llmRequest));
        Files.writeString(REPORT_DIR.resolve(name + "-api-response.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(apiResponse)));
    }
}
