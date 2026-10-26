package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 설계 4-5·7장: 정밀 추천 저장(response_id = null, session_id 있음), 재호출 시 같은 결과, 안전 규칙은 프로필 기준 그대로
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
class PersonalizedRecommendationApiTest {
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
    LlmCallLogRepository logRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 정밀_추천은_V1과_같은_형식으로_session_id로_저장되고_회차는_DONE() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        s = data(answer(user, s, List.of(0, 1)));
        stop(user, sessionId);

        JsonNode rec = data(recommend(user, sessionId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.responseId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].ingredient").value("VITAMIN_D")));
        long recId = rec.get("recommendationId").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select response_id, session_id from ai_recommendations where recommendation_id = ?", recId);
        assertThat(row.get("response_id")).isNull();
        assertThat(((Number) row.get("session_id")).longValue()).isEqualTo(sessionId);
        AiQuestionSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo("DONE");
        assertThat(session.getRecommendationId()).isEqualTo(recId);
        assertThat(session.getFinishedAt()).isNotNull();

        // 추천 프롬프트: 맞춤 답변 블록, 프롬프트 버전 -p, 회차 id 기록
        LlmRequest recommend = fake.requests().stream()
                .filter(r -> LlmRequest.TAG_RECOMMEND.equals(r.callTag())).findFirst().orElseThrow();
        assertThat(recommend.messages().get(0).text()).contains("## 맞춤 질문 답변")
                .contains("- [불편한 점] 가짜 질문: 요즘 불편한 점을 골라주세요 (최대 2개) → 쉽게 피곤해요, 잠을 잘 못 자요")
                .contains("현재 복용 중인 영양제 성분(하루 함량): 모름");
        assertThat(recommend.promptVersion()).endsWith("-p");
        assertThat(recommend.pipelineVersion()).isEqualTo("V4");
        assertThat(recommend.sessionId()).isEqualTo(sessionId);
        List<LlmCallLog> logs = logRepository.findAll().stream().filter(l -> Long.valueOf(sessionId).equals(l.getSessionId()))
                .toList();
        assertThat(logs).extracting(LlmCallLog::getCallTag).contains("PQ_QUESTION", "RECOMMEND");
    }

    @Test
    void 같은_회차로_다시_부르면_저장된_결과를_그대로_돌려준다() throws Exception {
        User user = members.fullMale(1980);
        long sessionId = data(start(user)).get("sessionId").asLong();
        stop(user, sessionId);
        long first = data(recommend(user, sessionId)).get("recommendationId").asLong();
        int calls = fake.requests().size();

        long second = data(recommend(user, sessionId).andExpect(status().isOk())).get("recommendationId").asLong();

        assertThat(second).isEqualTo(first);
        assertThat(fake.requests()).hasSize(calls);
    }

    @Test
    void READY가_아니면_PQ003_다른_회원은_404() throws Exception {
        User user = members.fullMale(1980);
        long sessionId = data(start(user)).get("sessionId").asLong();
        recommend(user, sessionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PQ003"));

        User other = members.fullMale(1981);
        stop(user, sessionId);
        recommend(other, sessionId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PQ002"));
        mockMvc.perform(post(BASE + "/" + sessionId + "/recommendation")).andExpect(status().isUnauthorized());
    }

    @Test
    void 끝까지_답한_뒤_정밀_추천() throws Exception {
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        while (s.get("question") != null && !s.get("question").isNull()) {
            List<Integer> pick = "MULTI".equals(s.at("/question/answerType").asString()) ? List.of(0, 1) : List.of(2);
            s = data(answer(user, s, pick));
        }
        assertThat(s.get("status").asString()).isEqualTo("READY");
        recommend(user, sessionId).andExpect(status().isOk());
        assertThat(sessionRepository.findById(sessionId).orElseThrow().isStoppedByUser()).isFalse();
    }

    @Test
    void 안전_규칙은_프로필과_임신_답_기준_그대로_비타민A_제외() throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("VITAMIN_A", "비타민 A", "가짜", null, null),
                new FakeAnswer.Item("VITAMIN_D", "비타민 D", "가짜", null, null)), "가짜"));
        User user = members.create(true, Map.of("gender", "F", "birth_year", Year.now().getValue() - 30),
                List.of("THYROID"), List.of("D-008"));
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        answer(user, s, List.of(2)); // 임신 중
        stop(user, sessionId);

        recommend(user, sessionId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].ingredient").value("VITAMIN_D"))
                .andExpect(jsonPath("$.data.excludedItems[0].ingredient").value("VITAMIN_A"))
                .andExpect(jsonPath("$.data.excludedItems[0].appliedRuleIds[0]").value("R-08"));
        // 임신 답은 프로필에 저장하지 않는다
        assertThat(jdbcTemplate.queryForList("select * from user_conditions where user_id = ?", user.getId()))
                .extracting(r -> r.get("condition_name")).containsExactly("THYROID");
    }

    @Test
    void 알림_진입_재사용은_V1_결과만_V2_결과는_쓰지_않는다() throws Exception {
        User user = members.fullMale(1980);
        long sessionId = data(start(user)).get("sessionId").asLong();
        stop(user, sessionId);
        recommend(user, sessionId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/recommendations/latest").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reusable").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_RECOMMENDATION"));
    }

    // ---------- helpers ----------

    private ResultActions start(User user) throws Exception {
        return mockMvc.perform(post(BASE).header("X-User-Id", user.getId()));
    }

    private ResultActions answer(User user, JsonNode s, List<Integer> selected) throws Exception {
        return mockMvc.perform(post(BASE + "/" + s.get("sessionId").asLong() + "/answers").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("turnId", s.at("/question/turnId").asLong(), "selected", selected))));
    }

    private void stop(User user, long sessionId) throws Exception {
        mockMvc.perform(post(BASE + "/" + sessionId + "/stop").header("X-User-Id", user.getId()))
                .andExpect(status().isOk());
    }

    private ResultActions recommend(User user, long sessionId) throws Exception {
        return mockMvc.perform(post(BASE + "/" + sessionId + "/recommendation").header("X-User-Id", user.getId()));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
