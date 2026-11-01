package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.guard.FeatureBudgetCounter;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyFixture.*;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게스트(비회원) 추천 보안 통합 테스트.
 * Guest Token(32-byte random → SHA-256 hash) 소유권 검증, feature 태그 분리, IDOR 방지.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuestRecommendationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired FakeLlmClient fake;
    @Autowired LlmCallLogRepository logRepository;
    @Autowired FeatureBudgetCounter budgetCounter;

    @AfterEach
    void reset() {
        fake.reset();
    }

    // ---------- 1. 정상 흐름 ----------

    @Test
    void 비회원_설문_토큰_추천생성_조회_전부_200() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995),
                choice(Q_SUN, SUN_LOW)), token);

        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", token[0])
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ingredient").value("VITAMIN_D"))
                .andReturn().getResponse().getContentAsString();
        long recId = objectMapper.readTree(body).get("data").get("recommendationId").asLong();

        mockMvc.perform(get("/api/v1/recommendations/" + recId)
                        .header("X-Guest-Token", token[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseId").value(responseId));
    }

    // ---------- 2. 토큰 없이 조회 → 404 ----------

    @Test
    void 토큰_없이_GET_추천은_404_R001() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_M)), token);
        long recId = createRecommendation(responseId, token[0]);

        mockMvc.perform(get("/api/v1/recommendations/" + recId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 3. 잘못된 토큰 → 404 ----------

    @Test
    void 잘못된_토큰으로_GET은_404_R001() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_M)), token);
        long recId = createRecommendation(responseId, token[0]);

        mockMvc.perform(get("/api/v1/recommendations/" + recId)
                        .header("X-Guest-Token", "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 4. ID 열거: 다른 게스트 rec에 내 토큰 → 404 ----------

    @Test
    void 다른_게스트_추천에_내_토큰은_404() throws Exception {
        // 게스트 A
        String[] tokenA = new String[1];
        long responseIdA = submitGuest(List.of(choice(Q_GENDER, GENDER_F)), tokenA);
        long recIdA = createRecommendation(responseIdA, tokenA[0]);

        // 게스트 B
        String[] tokenB = new String[1];
        submitGuest(List.of(choice(Q_GENDER, GENDER_M)), tokenB);

        // B의 토큰으로 A의 추천 접근
        mockMvc.perform(get("/api/v1/recommendations/" + recIdA)
                        .header("X-Guest-Token", tokenB[0]))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 5. 회원 JWT로 게스트 rec 접근 → 404 ----------

    @Test
    void 회원_JWT로_게스트_추천_접근은_404() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_F)), token);
        long recId = createRecommendation(responseId, token[0]);

        User user = TestUsers.create(userRepository, true);
        mockMvc.perform(get("/api/v1/recommendations/" + recId)
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 6. 게스트 토큰으로 회원 rec 접근 → 404 ----------

    @Test
    void 게스트_토큰으로_회원_추천_접근은_404() throws Exception {
        // 회원 추천 생성
        User user = TestUsers.create(userRepository, true);
        long memberResponseId = submitMember(user.getId(), List.of(choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1980)));
        String memberBody = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content("{\"responseId\":" + memberResponseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long memberRecId = objectMapper.readTree(memberBody).get("data").get("recommendationId").asLong();

        // 게스트 토큰으로 접근
        String[] guestToken = new String[1];
        submitGuest(List.of(choice(Q_GENDER, GENDER_F)), guestToken);
        mockMvc.perform(get("/api/v1/recommendations/" + memberRecId)
                        .header("X-Guest-Token", guestToken[0]))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 7. 토큰 없이 POST create → 404 ----------

    @Test
    void 토큰_없이_POST_추천_생성은_404() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_M)), token);

        mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 8. 설문 제출 응답에 guestToken 포함, 회원은 미포함 ----------

    @Test
    void 비회원_설문_응답에_guestToken_포함_회원은_없음() throws Exception {
        // 비회원
        String guestBody = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers",
                                List.of(choice(Q_GENDER, GENDER_F))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode guestData = objectMapper.readTree(guestBody).get("data");
        assertThat(guestData.has("guestToken")).isTrue();
        assertThat(guestData.get("guestToken").asString()).hasSize(64); // 32 bytes hex

        // 회원
        User user = TestUsers.create(userRepository, true);
        String memberBody = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers",
                                List.of(choice(Q_GENDER, GENDER_M))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode memberData = objectMapper.readTree(memberBody).get("data");
        assertThat(memberData.has("guestToken")).isFalse();
    }

    // ---------- 9. 같은 추천을 토큰 있으면 200, 없으면 404 ----------

    @Test
    void 토큰_유무에_따라_같은_추천이_200_또는_404() throws Exception {
        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_F)), token);
        long recId = createRecommendation(responseId, token[0]);

        // 토큰 있으면 200
        mockMvc.perform(get("/api/v1/recommendations/" + recId)
                        .header("X-Guest-Token", token[0]))
                .andExpect(status().isOk());

        // 토큰 없으면 404
        mockMvc.perform(get("/api/v1/recommendations/" + recId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ---------- 10. 비회원 V2/맞춤질문 API → 401 ----------

    @Test
    void 비회원은_V2_맞춤질문_API에_접근_불가() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/recommendations/latest"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 11. feature 태그: recommendation-guest ----------

    @Test
    void 비회원_추천은_recommendation_guest_feature로_기록된다() throws Exception {
        long before = budgetCounter.get(RecommendationService.FEATURE_GUEST);
        long beforeMember = budgetCounter.get("recommendation");

        String[] token = new String[1];
        long responseId = submitGuest(List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1990)), token);
        createRecommendation(responseId, token[0]);

        // FeatureBudgetCounter: recommendation-guest 증가, recommendation 그대로
        assertThat(budgetCounter.get(RecommendationService.FEATURE_GUEST)).isGreaterThan(before);
        assertThat(budgetCounter.get("recommendation")).isEqualTo(beforeMember);

        // LlmCallLog: feature = "recommendation-guest"
        List<LlmCallLog> logs = logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId);
        assertThat(logs).isNotEmpty();
        assertThat(logs).allSatisfy(log ->
                assertThat(log.getFeature()).isEqualTo(RecommendationService.FEATURE_GUEST));
    }

    // ---------- 12. 회원 추천은 recommendation feature ----------

    @Test
    void 회원_추천은_recommendation_feature로_기록된다() throws Exception {
        long before = budgetCounter.get("recommendation");
        long beforeGuest = budgetCounter.get(RecommendationService.FEATURE_GUEST);

        User user = TestUsers.create(userRepository, true);
        long responseId = submitMember(user.getId(), List.of(choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1985)));
        mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk());

        assertThat(budgetCounter.get("recommendation")).isGreaterThan(before);
        assertThat(budgetCounter.get(RecommendationService.FEATURE_GUEST)).isEqualTo(beforeGuest);
    }

    // ---------- helpers ----------

    private long submitGuest(List<Map<String, Object>> answers, String[] tokenHolder) throws Exception {
        String body = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        tokenHolder[0] = data.get("guestToken").asString();
        return data.get("responseId").asLong();
    }

    private long submitMember(Long userId, List<Map<String, Object>> answers) throws Exception {
        String body = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(userId))
                        .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("responseId").asLong();
    }

    private long createRecommendation(long responseId, String guestToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", guestToken)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("recommendationId").asLong();
    }
}
