package com.ucamp.app.domain.review;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 설문 제출 → 성분 추천(Fake LLM) → 제품 추천 API. 사용자 조건은 추천 결과의 설문·프로필에서 다시 만든다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class ReviewProductApiTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    AiQuestionSessionRepository sessionRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 비회원_마그네슘_B6_추천_결과에서_마그네슘_제품() throws Exception {
        long recId = recommend(null, List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995)), "MAGNESIUM", "VITAMIN_B6");

        String body = mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "MAGNESIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ingredient").value("MAGNESIUM"))
                .andExpect(jsonPath("$.data.products.length()").value(3))
                .andExpect(jsonPath("$.data.products[0].catalogProductId").value(9301))
                .andExpect(jsonPath("$.data.products[0].productName").value("나우 마그네슘 글리시네이트"))
                .andExpect(jsonPath("$.data.products[1].catalogProductId").value(9302))
                .andExpect(jsonPath("$.data.products[2].catalogProductId").value(9306))
                .andExpect(jsonPath("$.data.products[2].alsoContains[0]").value("VITAMIN_B6"))
                .andExpect(jsonPath("$.data.excludedCount").value(1))
                .andExpect(jsonPath("$.data.notice").value(ReviewProductResponse.notice(true)))
                .andReturn().getResponse().getContentAsString();

        // 약사·채널·리뷰 원문은 내보내지 않는다
        assertThat(body).doesNotContain("TESTCH").doesNotContain("context").doesNotContain("evidence").doesNotContain("예시 인용");

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.MAGNESIUM").value(true))
                .andExpect(jsonPath("$.data.VITAMIN_B6").value(true));
    }

    @Test
    void 회원_와파린_복용이면_비타민K가_같이_든_제품은_빠진다() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = recommend(user.getId(), List.of(choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1970),
                choice(Q_PREGNANCY, PREGNANCY_NA), text(Q_MEDICATIONS, "[{\"drugId\":\"D-007\"}]")), "VITAMIN_D");

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "VITAMIN_D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.length()").value(1))
                .andExpect(jsonPath("$.data.products[0].catalogProductId").value(9312))
                .andExpect(jsonPath("$.data.excludedCount").value(1))
                .andExpect(jsonPath("$.data.notice").value(ReviewProductResponse.MEMBER_NOTICE));
    }

    @Test
    void V2_맞춤_질문_추천은_회차의_임신_답으로_판단한다() throws Exception {
        User user = TestUsers.create(userRepository, true);
        AiQuestionSession session = AiQuestionSession.start(user);
        session.answerPregnancy("PREGNANT");
        sessionRepository.save(session);
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().session(session)
                .summary("가짜 정밀 추천").modelName("fake").status(AiRecommendation.DONE).build());
        itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName("OMEGA3")
                .reason("가짜 응답").sortOrder(1).build());

        // 9314(오메가3 + 루테인)는 임신이라 같이 든 루테인(R-38) 때문에 제외
        mockMvc.perform(get("/api/v1/recommendations/" + rec.getRecommendationId() + "/products")
                        .param("ingredient", "OMEGA3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.length()").value(1))
                .andExpect(jsonPath("$.data.products[0].catalogProductId").value(9315))
                .andExpect(jsonPath("$.data.excludedCount").value(1))
                .andExpect(jsonPath("$.data.notice").value(ReviewProductResponse.MEMBER_NOTICE));
    }

    @Test
    void 추천_결과에_없는_성분은_빈_목록과_안내() throws Exception {
        long recId = recommend(null, List.of(choice(Q_GENDER, GENDER_F)), "MAGNESIUM");

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "ZINC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.length()").value(0))
                .andExpect(jsonPath("$.data.message").value(ReviewProductResponse.EMPTY_MESSAGE));
    }

    @Test
    void 없는_추천은_404_잘못되거나_빠진_성분_코드는_400() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/999999/products").param("ingredient", "MAGNESIUM"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/recommendations/999999/products/availability"))
                .andExpect(status().isNotFound());

        long recId = recommend(null, List.of(choice(Q_GENDER, GENDER_F)), "MAGNESIUM");
        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "MELATONIN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availability는_통과_제품이_없는_성분을_false로() throws Exception {
        long recId = recommend(null, List.of(choice(Q_GENDER, GENDER_F)), "MAGNESIUM", "ZINC");

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.MAGNESIUM").value(true))
                .andExpect(jsonPath("$.data.ZINC").value(false));
    }

    private long recommend(Long userId, List<Map<String, Object>> answers, String... ingredients) throws Exception {
        fake.answerWith(new FakeAnswer(java.util.Arrays.stream(ingredients)
                .map(c -> new FakeAnswer.Item(c, null, "가짜 응답", BigDecimal.ONE, null)).toList(), "가짜 응답입니다"));
        var req = post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        String submitted = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(submitted).get("data").get("responseId").asLong();

        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        List<String> items = new java.util.ArrayList<>();
        data.get("items").forEach(i -> items.add(i.get("ingredient").asString()));
        assertThat(items).containsExactly(ingredients); // 성분 추천 결과가 의도대로인지 먼저 확인
        return data.get("recommendationId").asLong();
    }
}
