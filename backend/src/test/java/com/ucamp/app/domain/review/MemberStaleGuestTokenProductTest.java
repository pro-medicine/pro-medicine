package com.ucamp.app.domain.review;

import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.repository.SurveyResponseRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
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
import static com.ucamp.app.support.SurveyFixture.*;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 버그2: 로그인 회원이 sessionStorage에 남은 stale guestToken을 함께 보낼 때
 * 제품 추천 API(availability, recommend)가 정상 동작해야 한다.
 * RecommendationAccess가 회원 요청에서 guestToken을 무시하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class MemberStaleGuestTokenProductTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired FakeLlmClient fake;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 회원이_stale_guestToken과_함께_availability를_호출해도_200() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = memberRecommend(user);

        // 회원 JWT + stale guestToken 동시 전송
        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products/availability")
                        .header("Authorization", bearer(user.getId()))
                        .header("X-Guest-Token", "stale-token-from-previous-guest-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.MAGNESIUM").value(true));
    }

    @Test
    void 회원이_stale_guestToken과_함께_제품_추천을_호출해도_200() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = memberRecommend(user);

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products")
                        .param("ingredient", "MAGNESIUM")
                        .header("Authorization", bearer(user.getId()))
                        .header("X-Guest-Token", "stale-token-from-previous-guest-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredient").value("MAGNESIUM"));
    }

    private long memberRecommend(User user) throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("MAGNESIUM", null, "테스트", BigDecimal.ONE, null)),
                "테스트 요약"));

        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers",
                                List.of(choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1990))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long responseId = objectMapper.readTree(submitted).get("data").get("responseId").asLong();

        String body = mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("recommendationId").asLong();
    }
}
