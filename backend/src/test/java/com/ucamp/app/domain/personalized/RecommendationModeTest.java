package com.ucamp.app.domain.personalized;

import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 설계 D2: 성별·출생 연도, 민감정보 동의, 질환·복용 약 답함 중 하나라도 빠지면 V1
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
class RecommendationModeTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TestMembers members;

    private static final Map<String, Object> BASIC = Map.of("gender", "F", "birth_year", 1990);

    @Test
    void 모두_있으면_V2() throws Exception {
        User user = members.create(true, BASIC, List.of("HYPERTENSION"), List.of("D-001"));
        mode(user).andExpect(jsonPath("$.data.mode").value("V2"))
                .andExpect(jsonPath("$.data.v2").doesNotExist())
                .andExpect(jsonPath("$.data.missing.length()").value(0));
    }

    @Test
    void 성별이_없으면_V1() throws Exception {
        User user = members.create(true, Map.of("birth_year", 1990), List.of("HYPERTENSION"), List.of("D-001"));
        mode(user).andExpect(jsonPath("$.data.mode").value("V1"))
                .andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains("GENDER")));
    }

    @Test
    void 출생_연도가_없으면_V1() throws Exception {
        User user = members.create(true, Map.of("gender", "M"), List.of("HYPERTENSION"), List.of("D-001"));
        mode(user).andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains("BIRTH_YEAR")));
    }

    @Test
    void 프로필이_아예_없으면_성별_출생_연도_질환_약이_모두_빠진다() throws Exception {
        User user = members.create(true, Map.of(), List.of(), List.of());
        mode(user).andExpect(jsonPath("$.data.mode").value("V1"))
                .andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains(
                        "GENDER", "BIRTH_YEAR", "CONDITIONS_ANSWERED", "MEDICATIONS_ANSWERED")));
    }

    @Test
    void 민감정보_동의가_없으면_V1() throws Exception {
        User user = members.create(false, BASIC, List.of("HYPERTENSION"), List.of("D-001"));
        mode(user).andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains("SENSITIVE_CONSENT")));
    }

    @Test
    void 질환_약을_답한_적이_없으면_V1() throws Exception {
        User user = members.create(true, BASIC, List.of(), List.of());
        mode(user).andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains(
                "CONDITIONS_ANSWERED", "MEDICATIONS_ANSWERED")));
    }

    @Test
    void 설문에서_질환_약을_없음으로_답했으면_답한_것으로_본다() throws Exception {
        User user = members.create(true, BASIC, List.of(), List.of());
        var req = post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", user.getId())
                .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                        choice(Q_CONDITIONS, CONDITION_NONE), text(Q_MEDICATIONS, "[]")))));
        mockMvc.perform(req).andExpect(status().isOk());

        mode(user).andExpect(jsonPath("$.data.mode").value("V2"));
    }

    @Test
    void 비회원은_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/recommendation-mode")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));
    }

    private ResultActions mode(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/recommendation-mode").header("X-User-Id", user.getId()))
                .andExpect(status().isOk());
    }
}
