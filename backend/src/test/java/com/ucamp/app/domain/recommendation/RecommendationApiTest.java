package com.ucamp.app.domain.recommendation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

// 설문 제출 → 입력 조립 → 추천 생성(Fake LLM) → 조회
// 테스트는 기본으로 메트릭 내보내기를 끄므로 prometheus 확인을 위해 켠다
@AutoConfigureMetrics
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationApiTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    HealthProfileRepository healthProfileRepository;
    @Autowired
    RecommendationInputAssembler assembler;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    FakeLlmClient fake;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 비회원_설문_제출_후_추천_생성과_조회() throws Exception {
        long responseId = submit(null, List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995),
                choice(Q_SUN, SUN_LOW)));

        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ingredient").value("비타민 D"))
                .andReturn().getResponse().getContentAsString();
        long recommendationId = objectMapper.readTree(body).get("data").get("recommendationId").asLong();

        mockMvc.perform(get("/api/v1/recommendations/" + recommendationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseId").value(responseId))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        assertThat(logRepository.findByResponseIdOrderByLlmCallLogIdAsc(responseId)).hasSize(1);
    }

    @Test
    void 단계별_지연이_prometheus에_노출된다() throws Exception {
        long responseId = submit(null, List.of(choice(Q_GENDER, GENDER_M)));
        mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":" + responseId + "}")).andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus").accept(MediaType.TEXT_PLAIN)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics).contains("recommendation_stage_seconds")
                .contains("stage=\"assemble\"").contains("stage=\"llm\"").contains("stage=\"parse\"")
                .contains("recommendation_total_seconds");
    }

    @Test
    void 없는_추천_조회는_404_responseId_없으면_400() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/999999")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 입력_조립_회원은_설문에_없는_값을_프로필로_채운다() throws Exception {
        User user = TestUsers.create(userRepository, true);
        HealthProfile profile = HealthProfile.create(user);
        profile.applySurveyValue("drinking", "HIGH", null);
        profile.applySurveyValue("diet_type", "VEGAN", null);
        healthProfileRepository.saveAndFlush(profile);

        long responseId = submit(user.getId(), List.of(
                choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1980), choice(Q_SMOKING, SMOKING_ONE_PACK),
                choice(Q_PREGNANCY, PREGNANCY_NA), choice(Q_CONDITIONS, CONDITION_HYPERTENSION),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-003\"},{\"name\":\"기타 약\"}]"),
                text(Q_SUPPLEMENTS, "[{\"ingredient\":\"IRON\",\"amount\":10,\"unit\":\"mg\"}]")));

        RecommendationInput input = assembler.assemble(responseId);

        assertThat(input.respondentType()).isEqualTo("MEMBER");
        assertThat(input.smoking()).isEqualTo("ONE_PACK");
        assertThat(input.drinking()).isEqualTo("HIGH");   // 설문에 없고 프로필에 있음
        assertThat(input.dietType()).isEqualTo("VEGAN");
        assertThat(input.birthYear()).isEqualTo(1980);
        assertThat(input.pregnancy()).isEqualTo("NOT_APPLICABLE");
        assertThat(input.conditions()).containsExactly("HYPERTENSION");
        assertThat(input.medications()).extracting(RecommendationInput.Medication::drugClass)
                .containsExactly("ACE_INHIBITOR", null);
        assertThat(input.currentIntake()).extracting(RecommendationInput.Intake::ingredient).containsExactly("IRON");
        assertThat(input.responseId()).isEqualTo(responseId);
    }

    @Test
    void 입력_조립_비회원은_설문만_쓴다() throws Exception {
        long responseId = submit(null, List.of(choice(Q_GENDER, GENDER_F), choice(Q_DIET, DIET_VEGAN),
                choice(Q_GOAL, GOAL_SLEEP, GOAL_IMMUNITY),
                text(Q_SUPPLEMENTS, "[{\"ingredient\":\"VITAMIN_C\",\"amount\":100,\"unit\":\"mg\"}]")));

        RecommendationInput input = assembler.assemble(responseId);

        assertThat(input.isGuest()).isTrue();
        assertThat(input.gender()).isEqualTo("F");
        assertThat(input.dietType()).isEqualTo("VEGAN");
        assertThat(input.smoking()).isNull();
        assertThat(input.goals()).containsExactly("SLEEP", "IMMUNITY");
        assertThat(input.conditions()).isEmpty();
        assertThat(input.medications()).isEmpty();
        assertThat(input.currentIntake()).hasSize(1);
    }

    private long submit(Long userId, List<Map<String, Object>> answers) throws Exception {
        var req = post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        String body = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        return data.get("responseId").asLong();
    }
}
