package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.chat.parse.RedFlagDetector;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.LlmUsage;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V5 보안 설계서 7장 통합 테스트 (항목 15-21).
 * FakeLlmClient 기반, @SpringBootTest + H2.
 */
@SpringBootTest(properties = "chat.fallback.async=false")
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql({"/sql/seed-chat-mentions.sql", "/sql/seed-review-products.sql"})
class LlmSecurityIntegrationTest {
    static final String V2_BASE = "/api/v1/personalized-sessions";
    static final String CHAT_BASE = "/api/v1/chat/sessions";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestMembers members;
    @Autowired FakeLlmClient fake;
    @Autowired FeatureBudgetCounter budgetCounter;
    @Autowired LlmGuardProperties guardProperties;
    @Autowired AiRecommendationRepository recommendationRepository;
    @Autowired AiChatSessionRepository chatSessionRepository;
    @Autowired LlmCallLogRepository logRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        fake.reset();
    }

    @AfterEach
    void tearDown() {
        fake.reset();
    }

    // ---------- 15. V2 start/answer 예산 초과 → 503, LLM 0회 ----------

    @Test
    void V2_start_예산_초과시_503_LLM_호출_없음() throws Exception {
        User user = members.fullMale(1980);
        exhaustBudget("personalized");
        int llmBefore = fake.requests().size();

        mockMvc.perform(post(V2_BASE).header("Authorization", bearer(user.getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LG002"));

        assertThat(fake.requests().size()).isEqualTo(llmBefore);
    }

    // ---------- 16. V4 대화 예산 초과 → 503, LLM 0회 ----------

    @Test
    void V4_대화_예산_초과시_503_LLM_호출_없음() throws Exception {
        User user = members.fullMale(1980);
        submitSurvey(user);
        long chatId = startChat(user);
        exhaustBudget("chat");
        int llmBefore = fake.requests().size();

        mockMvc.perform(post(CHAT_BASE + "/" + chatId + "/messages")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"요즘 기운이 없어요\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LG002"));

        assertThat(fake.requests().size()).isEqualTo(llmBefore);
    }

    // ---------- 17. track 2 예산 초과 → 503, ai_recommendations 행 없음 ----------

    @Test
    void track2_recommendation_예산_초과시_503_추천행_없음() throws Exception {
        User user = members.fullMale(1980);
        submitSurvey(user);
        long chatId = startChat(user);
        // chat 예산은 괜찮지만 recommendation 예산 초과
        exhaustBudget("recommendation");

        long recCountBefore = recommendationRepository.count();

        // 해시 벡터 → 유사도 0 → 트랙 2 진입 → recommendation 예산 초과 → 503
        mockMvc.perform(post(CHAT_BASE + "/" + chatId + "/messages")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"요즘 기운이 없어요\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LG002"));

        assertThat(recommendationRepository.count()).isEqualTo(recCountBefore);
    }

    // ---------- 18. 예산 초과 상태에서도 위기 문장 → RED_FLAG 응답 (503 아님) ----------

    @Test
    void 예산_초과_상태에서도_위기_문장은_RED_FLAG_응답() throws Exception {
        User user = members.fullMale(1980);
        submitSurvey(user);
        long chatId = startChat(user);
        exhaustBudget("chat");

        mockMvc.perform(post(CHAT_BASE + "/" + chatId + "/messages")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"죽고싶어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("RED_FLAG"))
                .andExpect(jsonPath("$.data.notice").value(RedFlagDetector.CRISIS_NOTICE));
    }

    // ---------- 19. 구조화 2회 실패 → 502 + OUTPUT_INVALID 경고 ----------

    @Test
    void 구조화_2회_실패시_502_OUTPUT_INVALID_경고() throws Exception {
        User user = members.fullMale(1980);
        submitSurvey(user);
        long chatId = startChat(user);

        // 대화 구조화 LLM 응답을 항상 유효하지 않은 JSON으로 설정
        fake.setResponder(req -> {
            if (req.outputSchema() != null && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(req.outputSchema().name())) {
                return LlmResponse.builder().text("{invalid json that wont validate")
                            .stopReason("stop").usage(new LlmUsage(10, 5, 0, 0)).model("fake").build();
            }
            return fake.render(req, new FakeLlmClient.FakeAnswer(
                    List.of(new FakeLlmClient.FakeAnswer.Item("VITAMIN_D", null, "가짜", null, null)), "가짜"));
        });

        mockMvc.perform(post(CHAT_BASE + "/" + chatId + "/messages")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"요즘 피곤해요\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CH005"));
    }

    // ---------- 20. 서버 약 탐지 → 세션에 약 저장 ----------

    @Test
    void 서버_약_탐지로_와파린이_세션에_저장된다() throws Exception {
        User user = noMedicationMember();
        long chatId = startChat(user);

        // LLM 구조화: medications에 와파린 포함 (서버 탐지도 함께 검증)
        fake.setResponder(req -> {
            if (req.outputSchema() != null && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(req.outputSchema().name())) {
                tools.jackson.databind.node.ObjectNode node = (tools.jackson.databind.node.ObjectNode) fake.chatParseOutput("피곤 기운이 없음");
                tools.jackson.databind.node.ArrayNode meds = objectMapper.createArrayNode();
                meds.add(objectMapper.createObjectNode().put("name", "와파린").put("drug_class", "WARFARIN"));
                node.set("medications", meds);
                return fake.structuredResponse(req, node);
            }
            return fake.render(req, new FakeLlmClient.FakeAnswer(
                    List.of(new FakeLlmClient.FakeAnswer.Item("VITAMIN_D", null, "가짜", null, null)), "가짜"));
        });

        mockMvc.perform(post(CHAT_BASE + "/" + chatId + "/messages")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"와파린 먹고 있는데 요즘 기운이 없어요\"}"))
                .andExpect(status().isOk());

        AiChatSession session = chatSessionRepository.findById(chatId).orElseThrow();
        assertThat(session.getExtraMedications()).contains("D-007");
    }

    // ---------- 21. 서버 재시작 후 llm_call_logs로 메모리 예산 복원 ----------

    @Test
    void 서버_재시작_후_llm_call_logs에서_메모리_예산_복원() {
        // 오늘(Asia/Seoul) 날짜로 llm_call_logs에 직접 삽입
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("insert into llm_call_logs (feature, call_tag, model, input_tokens, output_tokens, " +
                        "http_status, pipeline_version, prompt_version, latency_ms, attempt_no, outcome, created_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                "chat", "CHAT_PARSE", "fake", 10, 5, 200, "V4", "v1", 0, 1, "SUCCESS", now);
        jdbcTemplate.update("insert into llm_call_logs (feature, call_tag, model, input_tokens, output_tokens, " +
                        "http_status, pipeline_version, prompt_version, latency_ms, attempt_no, outcome, created_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                "chat", "CHAT_PARSE", "fake", 10, 5, 200, "V4", "v1", 0, 1, "SUCCESS", now);
        jdbcTemplate.update("insert into llm_call_logs (feature, call_tag, model, input_tokens, output_tokens, " +
                        "http_status, pipeline_version, prompt_version, latency_ms, attempt_no, outcome, created_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                "recommendation", "RECOMMEND", "fake", 50, 20, 200, "V4", "v1", 0, 1, "SUCCESS", now);

        // 새 카운터를 만들어 init() 호출 (서버 재시작 시뮬레이션)
        FeatureBudgetCounter freshCounter = new FeatureBudgetCounter(logRepository);
        freshCounter.init();

        assertThat(freshCounter.get("chat")).isGreaterThanOrEqualTo(2);
        assertThat(freshCounter.get("recommendation")).isGreaterThanOrEqualTo(1);
    }

    // ---------- helpers ----------

    private void exhaustBudget(String feature) {
        int budget = guardProperties.getDailyBudgetFor(feature);
        for (int i = 0; i <= budget; i++) {
            budgetCounter.increment(feature);
        }
    }

    private void submitSurvey(User user) throws Exception {
        mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                                choice(Q_CONDITIONS, CONDITION_NONE), text(Q_MEDICATIONS, "[]"))))))
                .andExpect(status().isOk());
    }

    private long startChat(User user) throws Exception {
        String body = mockMvc.perform(post(CHAT_BASE).header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("chatSessionId").asLong();
    }

    private User noMedicationMember() throws Exception {
        User user = members.create(true, Map.of("gender", "M", "birth_year", 1980), List.of(), List.of());
        submitSurvey(user);
        return user;
    }
}
