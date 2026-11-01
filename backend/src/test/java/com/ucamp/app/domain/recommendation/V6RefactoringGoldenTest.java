package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.review.service.RecommendationConditionLoader;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.support.GoldenFiles;
import com.ucamp.app.support.TestMembers;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6 리팩터링 안전망. 후보별 핵심 동작을 골든 파일로 고정해 리팩터링 전후 차이를 감지한다.
 * - R-B3: BANNED_WORDS 목록 (RecommendationQualityCheck ↔ PersonalizedQuestionValidator 의존)
 * - R-B4: ConditionLoader.load() 결과 (엔티티 → 입력 출처 결정)
 * - R-B5: availability() 결과 (성분별 제품 유무 맵)
 * - R-B8: 대화 시작 응답 (프로필 중복 조회 구간)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class V6RefactoringGoldenTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired TestMembers members;
    @Autowired FakeLlmClient fake;
    @Autowired RecommendationConditionLoader conditionLoader;
    @Autowired ReviewProductRecommendationService reviewProductService;

    @AfterEach
    void reset() { fake.reset(); }

    /** R-B3: 금지어 목록이 리팩터링 전후로 변하지 않는다 (QualityCheck의 check 결과로 간접 검증) */
    @Test
    void R_B3_금지어가_요약에_있으면_검출된다() {
        // PersonalizedQuestionValidator.BANNED_WORDS + QualityCheck 자체 금지어가 모두 동작하는지 확인
        List<String> mustDetect = List.of("중단", "끊으세요", "진단", "처방", "복용을 멈추", "진료", "의료진", "검사 결과");
        for (String word : mustDetect) {
            String json = "{\"summary\":\"" + word + " 좋아요\",\"recommendations\":[{\"ingredient\":\"MAGNESIUM\",\"reason\":\"r\",\"dailyDose\":1}]}";
            tools.jackson.databind.JsonNode node = objectMapper.readTree(json);
            var result = com.ucamp.app.domain.recommendation.pipeline.RecommendationQualityCheck.check(node, List.of());
            org.assertj.core.api.Assertions.assertThat(result.summaryErrors())
                    .as("금지어 '%s'가 검출되어야 한다", word)
                    .isNotEmpty();
        }
        // 정상 요약은 통과
        String ok = "{\"summary\":\"건강에 좋아요\",\"recommendations\":[{\"ingredient\":\"MAGNESIUM\",\"reason\":\"r\",\"dailyDose\":1}]}";
        var okResult = com.ucamp.app.domain.recommendation.pipeline.RecommendationQualityCheck.check(
                objectMapper.readTree(ok), List.of());
        org.assertj.core.api.Assertions.assertThat(okResult.summaryErrors()).isEmpty();
    }

    /** R-B4: 설문 기반 추천 → ConditionLoader.load() 결과 (입력 출처·조건 코드) */
    @Test
    void R_B4_설문_추천의_조건_로더_결과() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = recommend(user.getId(), List.of(
                choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1970),
                choice(Q_SMOKING, SMOKING_HALF_PACK),
                choice(Q_CONDITIONS, CONDITION_HYPERTENSION),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-001\"}]")),
                "MAGNESIUM", "VITAMIN_D");

        RecommendationConditionLoader.Loaded loaded = conditionLoader.load(recId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recommendedIngredients", loaded.recommendedIngredients());
        if (loaded.user() != null) {
            snapshot.put("guest", loaded.user().guest());
            snapshot.put("hasMedicationOrDisease", loaded.user().hasMedicationOrDisease());
            snapshot.put("conditions", new TreeSet<>(loaded.user().conditions()));
            snapshot.put("recommendedInFilter", List.copyOf(loaded.user().recommendedIngredients()));
        }
        GoldenFiles.assertMatches("v6/condition-loader", snapshot);
    }

    /** R-B5: availability() 결과 — 성분별 안전 필터 통과 제품 유무 맵 */
    @Test
    void R_B5_availability_결과_맵() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = recommend(user.getId(), List.of(choice(Q_GENDER, GENDER_F)),
                "MAGNESIUM", "ZINC");

        Map<String, Boolean> availability = reviewProductService.availability(recId);
        GoldenFiles.assertMatches("v6/availability", availability);
    }

    /** R-B8: 대화 시작 응답 구조 — 남성(임신 질문 없음) vs 여성(임신 질문 있음) */
    @Test
    void R_B8_대화_시작_응답_구조() throws Exception {
        User male = members.fullMale(1980);
        JsonNode maleData = chatStart(male);

        User female = members.create(true,
                Map.of("gender", "F", "birth_year", Year.now().getValue() - 30),
                List.of("HYPERTENSION"), List.of("D-001"));
        JsonNode femaleData = chatStart(female);

        // chatSessionId는 동적이므로 구조와 임신 질문만 비교
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("male", Map.of(
                "hasPregnancyQuestion", !maleData.path("pregnancyQuestion").isMissingNode()
                        && !maleData.path("pregnancyQuestion").isNull()
        ));
        snapshot.put("female", Map.of(
                "hasPregnancyQuestion", !femaleData.path("pregnancyQuestion").isMissingNode()
                        && !femaleData.path("pregnancyQuestion").isNull(),
                "pregnancyQuestion", femaleData.path("pregnancyQuestion")
        ));
        GoldenFiles.assertMatches("v6/chat-start", snapshot);
    }

    // ---------- 도우미 ----------

    private JsonNode chatStart(User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long recommend(Long userId, List<Map<String, Object>> answers, String... ingredients) throws Exception {
        fake.answerWith(new FakeAnswer(java.util.Arrays.stream(ingredients)
                .map(c -> new FakeAnswer.Item(c, null, "가짜 응답", BigDecimal.ONE, null)).toList(), "가짜 응답입니다"));
        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(userId))
                        .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(submitted).path("data").path("responseId").asLong();

        String body = mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(userId))
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("recommendationId").asLong();
    }
}
