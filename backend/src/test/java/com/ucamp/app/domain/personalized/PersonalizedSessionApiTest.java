package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 설계 4장: 회차 시작·답변·그만하기 (Fake LLM). 정밀 추천은 PersonalizedRecommendationApiTest
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
class PersonalizedSessionApiTest {
    static final String BASE = "/api/v1/personalized-sessions";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TestMembers members;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    AiQuestionSessionRepository sessionRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 남성은_임신_질문_없이_첫_LLM_질문_불편한_점() throws Exception {
        User user = members.fullMale(1980);
        start(user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.question.fixed").value(false))
                .andExpect(jsonPath("$.data.question.turnNo").value(1))
                .andExpect(jsonPath("$.data.question.topic").value("CONCERN_OPEN"))
                .andExpect(jsonPath("$.data.question.answerType").value("MULTI"))
                .andExpect(jsonPath("$.data.question.maxSelect").value(2))
                .andExpect(jsonPath("$.data.question.options[0].index").value(0))
                .andExpect(jsonPath("$.data.question.options[0].kind").doesNotExist())
                .andExpect(jsonPath("$.data.question.options[0].exclusive").value(false))
                .andExpect(jsonPath("$.data.question.options[2].exclusive").value(true))
                .andExpect(jsonPath("$.data.progress.asked").value(1))
                .andExpect(jsonPath("$.data.progress.base").value(5))
                .andExpect(jsonPath("$.data.progress.max").value(7));
    }

    @Test
    void 여성_19세에서_50세는_임신_고정_질문부터_LLM_호출_없음() throws Exception {
        int year = Year.now().getValue();
        for (int age : List.of(19, 50)) {
            fake.reset();
            User user = female(year - age);
            start(user).andExpect(jsonPath("$.data.question.fixed").value(true))
                    .andExpect(jsonPath("$.data.question.turnNo").value(0))
                    .andExpect(jsonPath("$.data.question.topic").value("PREGNANCY"))
                    .andExpect(jsonPath("$.data.question.options.length()").value(4))
                    .andExpect(jsonPath("$.data.progress.asked").value(0));
            assertThat(fake.requests()).isEmpty();
        }
        for (int age : List.of(18, 51)) {
            User user = female(year - age);
            start(user).andExpect(jsonPath("$.data.question.topic").value("CONCERN_OPEN"));
        }
    }

    @Test
    void 임신_답은_회차에만_저장되고_다음은_불편한_점() throws Exception {
        User user = female(Year.now().getValue() - 30);
        JsonNode s = data(start(user));
        JsonNode next = data(answer(user, s, List.of(2)).andExpect(status().isOk()));

        assertThat(next.at("/question/topic").asString()).isEqualTo("CONCERN_OPEN");
        assertThat(sessionRepository.findById(s.get("sessionId").asLong()).orElseThrow().getPregnancy())
                .isEqualTo("PREGNANT");
        assertThat(fake.requests().get(0).messages().get(0).text()).contains("임신·수유: 임신 중");
    }

    @Test
    void 끝까지_답하면_READY_질문은_서버_계획대로() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        List<String> topics = new ArrayList<>();
        while (s.get("question") != null && !s.get("question").isNull()) {
            topics.add(s.at("/question/topic").asString() + "#" + s.at("/question/turnNo").asInt());
            // Fake 선택지: MULTI는 CONCERN 2개(0,1), SINGLE은 0번이 CONCERN
            List<Integer> pick = "MULTI".equals(s.at("/question/answerType").asString()) ? List.of(0, 1) : List.of(0);
            s = data(answer(user, s, pick).andExpect(status().isOk()));
        }
        assertThat(s.get("status").asString()).isEqualTo("READY");
        // 설계 5-5 예시와 같은 경로 (약물 우선): 불편한 점 → 약 2단계 → 피곤 2단계 → 5문항 뒤 새 주제 불가
        assertThat(topics).containsExactly("CONCERN_OPEN#1", "MED:METFORMIN#2", "MED:METFORMIN#3",
                "CONCERN:쉽게 피곤해요#4", "CONCERN:쉽게 피곤해요#5");
        assertThat(s.at("/progress/asked").asInt()).isEqualTo(5);
        // 맞춤 답변은 프로필에 저장하지 않는다
        assertThat(jdbcTemplate.queryForObject("select count(*) from user_conditions where user_id = ?", Integer.class,
                user.getId())).isEqualTo(1);
    }

    @Test
    void 잘못된_답은_PQ004() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        long turnId = s.at("/question/turnId").asLong();

        answer(user, sessionId, turnId + 999, List.of(0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PQ004"));
        answer(user, sessionId, turnId, List.of()).andExpect(jsonPath("$.code").value("PQ004"));
        answer(user, sessionId, turnId, List.of(0, 1, 2)).andExpect(jsonPath("$.code").value("PQ004")); // MULTI 최대 2
        answer(user, sessionId, turnId, List.of(9)).andExpect(jsonPath("$.code").value("PQ004"));      // 범위
        answer(user, sessionId, turnId, List.of(0, 0)).andExpect(jsonPath("$.code").value("PQ004"));   // 중복
        answer(user, sessionId, turnId, List.of(0, 2)).andExpect(jsonPath("$.code").value("PQ004"));   // NONE과 함께

        JsonNode next = data(answer(user, sessionId, turnId, List.of(0)).andExpect(status().isOk()));
        long single = next.at("/question/turnId").asLong();
        answer(user, sessionId, single, List.of(0, 1)).andExpect(jsonPath("$.code").value("PQ004")); // SINGLE 1개
        answer(user, sessionId, turnId, List.of(0)).andExpect(jsonPath("$.code").value("PQ004"));    // 이미 답한 질문
    }

    @Test
    void 그만하기는_답하지_않은_질문을_버리고_READY_이후_답변은_PQ003() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        JsonNode s2 = data(answer(user, s, List.of(0)));
        long sessionId = s.get("sessionId").asLong();

        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.progress.asked").value(1));
        AiQuestionSession saved = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(saved.isStoppedByUser()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_question_turns where session_id = ?",
                Integer.class, sessionId)).isEqualTo(1);

        answer(user, sessionId, s2.at("/question/turnId").asLong(), List.of(0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PQ003"));
        // 다시 그만하기는 그대로 READY
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void 답한_LLM_질문이_0개면_결과_보기는_PQ003() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        assertThat(s.get("canStop").asBoolean()).isFalse();
        mockMvc.perform(post(BASE + "/" + s.get("sessionId").asLong() + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PQ003"));
        assertThat(data(answer(user, s, List.of(0))).get("canStop").asBoolean()).isTrue();
    }

    @Test
    void 임신_질문에_답하기_전에는_결과_보기_PQ003() throws Exception {
        User user = female(Year.now().getValue() - 30);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PQ003"));

        // 임신 질문만 답해도 아직 안 됨, LLM 질문까지 답하면 가능
        JsonNode s2 = data(answer(user, s, List.of(0)));
        assertThat(s2.get("canStop").asBoolean()).isFalse();
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict());
        data(answer(user, s2, List.of(0)));
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void 닫기는_추천_없이_ABANDONED_사용자_중단_이후_답변은_PQ003() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();

        mockMvc.perform(post(BASE + "/" + sessionId + "/close").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABANDONED"))
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.progress.asked").value(0))
                .andExpect(jsonPath("$.data.canStop").value(false));
        AiQuestionSession saved = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(saved.isStoppedByUser()).isTrue();
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_question_turns where session_id = ?",
                Integer.class, sessionId)).isZero();

        answer(user, s, List.of(0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PQ003"));
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/" + sessionId + "/recommendation").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict());
        // 다시 닫기는 그대로
        mockMvc.perform(post(BASE + "/" + sessionId + "/close").header("X-User-Id", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ABANDONED"));
        mockMvc.perform(post(BASE + "/" + sessionId + "/close")).andExpect(status().isUnauthorized());
    }

    @Test
    void READY_회차도_닫을_수_있다() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        answer(user, s, List.of(0));
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(jsonPath("$.data.status").value("READY"));
        mockMvc.perform(post(BASE + "/" + sessionId + "/close").header("X-User-Id", user.getId()))
                .andExpect(jsonPath("$.data.status").value("ABANDONED"));
    }

    @Test
    void 새로_시작하면_진행_중이던_회차는_ABANDONED() throws Exception {
        User user = members.fullMale(1980);
        long first = data(start(user)).get("sessionId").asLong();
        long second = data(start(user)).get("sessionId").asLong();

        assertThat(second).isNotEqualTo(first);
        assertThat(sessionRepository.findById(first).orElseThrow().getStatus()).isEqualTo("ABANDONED");
        assertThat(sessionRepository.findById(second).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void 다른_회원의_회차는_404_비회원은_401() throws Exception {
        User owner = members.fullMale(1980);
        User other = members.fullMale(1981);
        JsonNode s = data(start(owner));

        answer(other, s, List.of(0)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PQ002"));
        mockMvc.perform(post(BASE + "/" + s.get("sessionId").asLong() + "/stop").header("X-User-Id", other.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("E003"));
        mockMvc.perform(post(BASE + "/1/stop")).andExpect(status().isUnauthorized());
    }

    @Test
    void 건강정보가_부족하면_409_PQ001과_missing() throws Exception {
        User user = members.create(false, Map.of("gender", "M"), List.of(), List.of());
        start(user).andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PQ001"))
                .andExpect(jsonPath("$.data.missing").value(org.hamcrest.Matchers.contains(
                        "BIRTH_YEAR", "SENSITIVE_CONSENT", "CONDITIONS_ANSWERED", "MEDICATIONS_ANSWERED")));
    }

    @Test
    void 질문_생성이_재요청까지_실패하면_502_PQ005_회차_FAILED() throws Exception {
        User user = members.fullMale(1980);
        fake.setResponder(req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, 500, null, 0, "boom", null);
        });
        start(user).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PQ005"))
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요."));
        List<AiQuestionSession> sessions = sessionRepository.findByUser_IdAndStatusIn(user.getId(), List.of("FAILED"));
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getErrorCode()).isEqualTo("PQ005");
    }

    // ---------- helpers ----------

    private User female(int birthYear) {
        return members.create(true, Map.of("gender", "F", "birth_year", birthYear), List.of("THYROID"), List.of("D-007"));
    }

    private ResultActions start(User user) throws Exception {
        return mockMvc.perform(post(BASE).header("X-User-Id", user.getId()));
    }

    private ResultActions answer(User user, JsonNode s, List<Integer> selected) throws Exception {
        return answer(user, s.get("sessionId").asLong(), s.at("/question/turnId").asLong(), selected);
    }

    private ResultActions answer(User user, long sessionId, long turnId, List<Integer> selected) throws Exception {
        return mockMvc.perform(post(BASE + "/" + sessionId + "/answers").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("turnId", turnId, "selected", selected))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
