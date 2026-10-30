package com.ucamp.app.domain.survey;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.entity.UserCondition;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.health.repository.UserConditionRepository;
import com.ucamp.app.domain.health.repository.UserMedicationRepository;
import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.repository.SurveyAnswerRepository;
import com.ucamp.app.domain.survey.repository.SurveyResponseRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SurveyApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    HealthProfileRepository healthProfileRepository;
    @Autowired
    UserConditionRepository userConditionRepository;
    @Autowired
    UserMedicationRepository userMedicationRepository;
    @Autowired
    SurveyResponseRepository surveyResponseRepository;
    @Autowired
    SurveyAnswerRepository surveyAnswerRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    // ---------- 설문 시작 ----------

    @Test
    void 비회원_설문_시작_민감_문항이_없다() throws Exception {
        JsonNode data = getActive(null);

        assertThat(data.get("respondentType").asString()).isEqualTo("GUEST");
        List<String> codes = codes(data);
        assertThat(codes).hasSize(11)
                .doesNotContain("Q-PREGNANCY", "Q-CONDITIONS", "Q-MEDICATIONS");
        for (JsonNode q : data.get("questions")) {
            assertThat(q.get("sensitive").asBoolean()).isFalse();
            assertThat(q.has("prefill")).isFalse();
        }
    }

    @Test
    void 동의한_회원_설문_시작_민감_문항과_흡연_미리채우기() throws Exception {
        User user = TestUsers.create(userRepository, true);
        HealthProfile profile = HealthProfile.create(user);
        profile.applySurveyValue("smoking", "HALF_PACK", null);
        healthProfileRepository.saveAndFlush(profile);

        JsonNode data = getActive(user.getId());

        assertThat(data.get("respondentType").asString()).isEqualTo("MEMBER");
        assertThat(codes(data)).hasSize(14).contains("Q-PREGNANCY", "Q-CONDITIONS", "Q-MEDICATIONS");
        JsonNode smoking = question(data, "Q-SMOKING");
        assertThat(smoking.get("prefill").get("optionIds").get(0).asLong()).isEqualTo(SMOKING_HALF_PACK);
        assertThat(data.get("needsConfirm").asBoolean()).isFalse();
    }

    @Test
    void 동의하지_않은_회원은_민감_문항이_없다() throws Exception {
        User user = TestUsers.create(userRepository, false);

        JsonNode data = getActive(user.getId());

        assertThat(codes(data)).hasSize(11).doesNotContain("Q-PREGNANCY", "Q-CONDITIONS", "Q-MEDICATIONS");
    }

    @Test
    void 질환과_복용약_미리채우기() throws Exception {
        User user = TestUsers.create(userRepository, true);
        submit(user.getId(), List.of(
                choice(Q_CONDITIONS, CONDITION_HYPERTENSION, CONDITION_DIABETES),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-003\"},{\"name\":\"마스터에 없는 약\"}]")))
                .andExpect(status().isOk());

        JsonNode data = getActive(user.getId());

        assertThat(question(data, "Q-CONDITIONS").get("prefill").get("optionIds").size()).isEqualTo(2);
        JsonNode meds = objectMapper.readTree(question(data, "Q-MEDICATIONS").get("prefill").get("valueText").asString());
        assertThat(meds.get(0).get("drugId").asString()).isEqualTo("D-003");
        assertThat(meds.get(1).get("name").asString()).isEqualTo("마스터에 없는 약");
    }

    @Test
    void 프로필이_6개월_지났으면_needsConfirm() throws Exception {
        User user = TestUsers.create(userRepository, true);
        HealthProfile profile = HealthProfile.create(user);
        profile.applySurveyValue("smoking", "NONE", null);
        healthProfileRepository.saveAndFlush(profile);
        jdbcTemplate.update("update health_profiles set updated_at = ? where user_id = ?",
                LocalDateTime.now().minusMonths(7), user.getId());
        entityManager.clear();

        JsonNode data = getActive(user.getId());

        assertThat(data.get("needsConfirm").asBoolean()).isTrue();
    }

    // ---------- 제출 ----------

    @Test
    void 회원_제출_응답_답_프로필_질환_반영() throws Exception {
        User user = TestUsers.create(userRepository, true);
        // 기존 질환은 교체되어야 한다
        userConditionRepository.saveAndFlush(UserCondition.builder().user(user).conditionName("KIDNEY").build());

        long responseId = submitOk(user.getId(), List.of(
                choice(Q_GENDER, GENDER_M),
                number(Q_BIRTH, 1990),
                choice(Q_SMOKING, SMOKING_ONE_PACK),
                choice(Q_DRINKING, DRINKING_HIGH),
                choice(Q_SLEEP, SLEEP_POOR),
                choice(Q_GOAL, GOAL_SLEEP, GOAL_IMMUNITY),
                choice(Q_CONDITIONS, CONDITION_HYPERTENSION, CONDITION_DIABETES),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-003\"}]"),
                text(Q_SUPPLEMENTS, "[{\"ingredient\":\"MAGNESIUM\",\"amount\":30,\"unit\":\"mg\"}]")));
        entityManager.flush();
        entityManager.clear();

        SurveyResponse response = surveyResponseRepository.findById(responseId).orElseThrow();
        assertThat(response.getRespondentType()).isEqualTo(RespondentType.MEMBER);
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
        assertThat(response.getIncludesSensitive()).isTrue();
        assertThat(response.getSubmittedAt()).isNotNull();
        assertThat(surveyResponseRepository.count()).isEqualTo(1);
        // 단일 7개 + 목표 2개 + 질환 2개 = 11행 (TEXT 2개 포함)
        assertThat(surveyAnswerRepository.countByResponse_ResponseId(responseId)).isEqualTo(11);

        HealthProfile profile = healthProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getGender()).isEqualTo("M");
        assertThat(profile.getBirthYear()).isEqualTo(1990);
        assertThat(profile.getSmoking()).isEqualTo("ONE_PACK");
        assertThat(profile.getDrinking()).isEqualTo("HIGH");

        assertThat(userConditionRepository.findByUser_IdOrderByUserConditionIdAsc(user.getId()))
                .extracting(UserCondition::getConditionName)
                .containsExactly("HYPERTENSION", "DIABETES");
        assertThat(userMedicationRepository.findWithDrugByUserId(user.getId()))
                .extracting(m -> m.getDrug().getDrugClass())
                .containsExactly("ACE_INHIBITOR");
    }

    @Test
    void 질환_없음을_고르면_기존_질환만_삭제() throws Exception {
        User user = TestUsers.create(userRepository, true);
        userConditionRepository.saveAndFlush(UserCondition.builder().user(user).conditionName("KIDNEY").build());

        submitOk(user.getId(), List.of(choice(Q_CONDITIONS, CONDITION_NONE), text(Q_MEDICATIONS, "[]")));

        assertThat(userConditionRepository.findByUser_IdOrderByUserConditionIdAsc(user.getId())).isEmpty();
        assertThat(userMedicationRepository.findWithDrugByUserId(user.getId())).isEmpty();
    }

    @Test
    void 비회원_제출_프로필_테이블_변화_없음() throws Exception {
        long profilesBefore = healthProfileRepository.count();
        long conditionsBefore = userConditionRepository.count();

        long responseId = submitOk(null, List.of(
                choice(Q_GENDER, GENDER_F),
                choice(Q_SMOKING, SMOKING_NONE),
                text(Q_SUPPLEMENTS, "[]")));
        entityManager.flush();
        entityManager.clear();

        SurveyResponse response = surveyResponseRepository.findById(responseId).orElseThrow();
        assertThat(response.getUser()).isNull();
        assertThat(response.getRespondentType()).isEqualTo(RespondentType.GUEST);
        assertThat(response.getIncludesSensitive()).isFalse();
        assertThat(healthProfileRepository.count()).isEqualTo(profilesBefore);
        assertThat(userConditionRepository.count()).isEqualTo(conditionsBefore);
    }

    // ---------- 검증 실패 ----------

    @Test
    void 비회원이_민감_문항_답을_보내면_400() throws Exception {
        submit(null, List.of(choice(Q_PREGNANCY, PREGNANCY_NA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    void 동의_없는_회원이_민감_문항_답을_보내면_403() throws Exception {
        User user = TestUsers.create(userRepository, false);
        submit(user.getId(), List.of(choice(Q_CONDITIONS, CONDITION_NONE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("S004"));
    }

    @Test
    void 다른_설문의_문항이면_400() throws Exception {
        submit(null, List.of(choice(9999L, GENDER_M)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("S002"));
    }

    @Test
    void 문항의_선택지가_아닌_option이면_400() throws Exception {
        submit(null, List.of(choice(Q_GENDER, SMOKING_NONE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void SINGLE_문항에_답이_2개면_400() throws Exception {
        submit(null, List.of(choice(Q_GENDER, GENDER_M, GENDER_F)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 질환_없음과_다른_질환을_같이_고르면_400() throws Exception {
        User user = TestUsers.create(userRepository, true);
        submit(user.getId(), List.of(choice(Q_CONDITIONS, CONDITION_NONE, CONDITION_HYPERTENSION)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 복용약_JSON_형식이_틀리면_400() throws Exception {
        User user = TestUsers.create(userRepository, true);
        submit(user.getId(), List.of(text(Q_MEDICATIONS, "{not json")))
                .andExpect(status().isBadRequest());
        submit(user.getId(), List.of(text(Q_MEDICATIONS, "[{\"drugId\":\"D-001\",\"name\":\"둘 다\"}]")))
                .andExpect(status().isBadRequest());
        submit(user.getId(), List.of(text(Q_MEDICATIONS, "[{\"drugId\":\"D-404\"}]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 영양제_JSON_형식이_틀리거나_모르는_성분이면_400() throws Exception {
        submit(null, List.of(text(Q_SUPPLEMENTS, "[{\"ingredient\":\"UNKNOWN\",\"amount\":1,\"unit\":\"mg\"}]")))
                .andExpect(status().isBadRequest());
        submit(null, List.of(text(Q_SUPPLEMENTS, "[{\"ingredient\":\"MAGNESIUM\",\"amount\":\"30\",\"unit\":\"mg\"}]")))
                .andExpect(status().isBadRequest());
        submit(null, List.of(text(Q_SUPPLEMENTS, "[{\"ingredient\":\"MAGNESIUM\",\"amount\":-1,\"unit\":\"mg\"}]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_문항_답이_중복되면_400() throws Exception {
        submit(null, List.of(choice(Q_GENDER, GENDER_M), choice(Q_GENDER, GENDER_F)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 동의 · 기준 데이터 ----------

    @Test
    void 민감정보_동의() throws Exception {
        User user = TestUsers.create(userRepository, false);
        mockMvc.perform(post("/api/v1/users/me/sensitive-consent").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sensitiveConsent").value(true));
        mockMvc.perform(post("/api/v1/users/me/sensitive-consent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 약_검색과_성분_목록() throws Exception {
        mockMvc.perform(get("/api/v1/drugs").param("keyword", "와파린"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].drugClass").value("WARFARIN"));
        mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(24));
    }

    // ---------- helpers ----------

    private JsonNode getActive(Long userId) throws Exception {
        var req = get("/api/v1/surveys/active");
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        String body = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private ResultActions submit(Long userId, List<Map<String, Object>> answers) throws Exception {
        var req = post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        return mockMvc.perform(req);
    }

    private long submitOk(Long userId, List<Map<String, Object>> answers) throws Exception {
        String body = submit(userId, answers).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("responseId").asLong();
    }

    private static List<String> codes(JsonNode data) {
        List<String> codes = new ArrayList<>();
        data.get("questions").forEach(q -> codes.add(q.get("code").asString()));
        return codes;
    }

    private static JsonNode question(JsonNode data, String code) {
        for (JsonNode q : data.get("questions")) {
            if (q.get("code").asString().equals(code)) {
                return q;
            }
        }
        throw new AssertionError("no question " + code);
    }
}
