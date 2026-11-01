package com.ucamp.app.domain.review;

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
 * 작업3: 제품 추천은 회원 전용. 비회원은 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class GuestProductRestrictionTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired FakeLlmClient fake;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 비회원_availability는_401() throws Exception {
        String guestToken = guestRecommend();

        mockMvc.perform(get("/api/v1/recommendations/1/products/availability")
                        .header("X-Guest-Token", guestToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비회원_products는_401() throws Exception {
        String guestToken = guestRecommend();

        mockMvc.perform(get("/api/v1/recommendations/1/products")
                        .param("ingredient", "MAGNESIUM")
                        .header("X-Guest-Token", guestToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_availability는_정상_200() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = memberRecommend(user);

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products/availability")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.MAGNESIUM").value(true));
    }

    @Test
    void 회원_products는_정상_200() throws Exception {
        User user = TestUsers.create(userRepository, true);
        long recId = memberRecommend(user);

        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products")
                        .param("ingredient", "MAGNESIUM")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredient").value("MAGNESIUM"));
    }

    private String guestRecommend() throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("MAGNESIUM", null, "테스트", BigDecimal.ONE, null)),
                "테스트"));

        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers",
                                List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(submitted).get("data");
        long responseId = data.get("responseId").asLong();
        String guestToken = data.has("guestToken") ? data.get("guestToken").asString() : null;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", guestToken)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk());

        return guestToken;
    }

    private long memberRecommend(User user) throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("MAGNESIUM", null, "테스트", BigDecimal.ONE, null)),
                "테스트"));

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
