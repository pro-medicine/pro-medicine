package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.config.PersonalizedProperties;
import com.ucamp.app.domain.personalized.prefetch.PrefetchStrategy;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.support.TestMembers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 질문 미리 만들기 (pq.prefetch-strategy) 방식별 호출 수·적중·단건 대체 (Fake LLM).
 * 회원: 남 46세, 메트포르민(MED, 최대 2단계), 고혈압(CONDITION), 흡연(LIFESTYLE) → 프로필 주제 3개
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
class PersonalizedPrefetchTest {
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
    PersonalizedProperties properties;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MeterRegistry meterRegistry;

    @AfterEach
    void reset() {
        fake.reset();
        properties.setPrefetchStrategy(PrefetchStrategy.NONE);
    }

    @Test
    void BACKGROUND_첫_질문은_단건_나머지는_백그라운드_묶음에서() throws Exception {
        properties.setPrefetchStrategy(PrefetchStrategy.BACKGROUND);
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        assertThat(s.at("/question/topic").asString()).isEqualTo("CONCERN_OPEN");

        s = data(answer(user, s, List.of(2)));           // 불편한 점: 없어요
        assertThat(s.at("/question/topic").asString()).isEqualTo("MED:METFORMIN");
        s = data(answer(user, s, List.of(1)));           // NORMAL → 다음 주제
        s = data(answer(user, s, List.of(1)));
        assertThat(s.at("/question/topic").asString()).isEqualTo("LIFESTYLE:SMOKING");
        s = data(answer(user, s, List.of(1)));
        assertThat(s.get("status").asString()).isEqualTo("READY");

        assertThat(tags()).containsExactlyInAnyOrder(LlmRequest.TAG_PQ_QUESTION, LlmRequest.TAG_PQ_QUESTION_BATCH);
        Long batchLog = batchLogId(sessionId);
        assertThat(turnLogIds(sessionId)).containsExactly(singleLogId(sessionId), batchLog, batchLog, batchLog);
    }

    @Test
    void UPFRONT_시작_때_불편한_점까지_한_번에_만들고_질문마다_호출하지_않는다() throws Exception {
        properties.setPrefetchStrategy(PrefetchStrategy.UPFRONT);
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        assertThat(tags()).containsExactly(LlmRequest.TAG_PQ_QUESTION_BATCH);
        assertThat(s.at("/question/answerType").asString()).isEqualTo("MULTI");

        while (s.get("question") != null && !s.get("question").isNull()) {
            s = data(answer(user, s, "MULTI".equals(s.at("/question/answerType").asString()) ? List.of(2) : List.of(1)));
        }
        assertThat(tags()).containsExactly(LlmRequest.TAG_PQ_QUESTION_BATCH);
        assertThat(turnLogIds(sessionId)).hasSize(4).containsOnly(batchLogId(sessionId));
    }

    @Test
    void UPFRONT_FOLLOWUP_파생_주제_묶음과_CONCERN일_때의_2단계까지_미리() throws Exception {
        properties.setPrefetchStrategy(PrefetchStrategy.UPFRONT_FOLLOWUP);
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        s = data(answer(user, s, List.of(0, 1)));         // 쉽게 피곤해요, 잠을 잘 못 자요 → 파생 주제 2개
        assertThat(s.at("/question/topic").asString()).isEqualTo("MED:METFORMIN");
        s = data(answer(user, s, List.of(0)));           // CONCERN → 2단계 (미리 만든 followup)
        assertThat(s.at("/question/text").asString()).contains("MED:METFORMIN 2단계");
        s = data(answer(user, s, List.of(1)));
        assertThat(s.at("/question/topic").asString()).isEqualTo("CONCERN:쉽게 피곤해요");
        s = data(answer(user, s, List.of(1)));
        assertThat(s.at("/question/topic").asString()).isEqualTo("CONCERN:잠을 잘 못 자요");
        s = data(answer(user, s, List.of(1)));
        assertThat(s.get("status").asString()).isEqualTo("READY");   // 새 주제 예산 5 소진

        assertThat(tags()).containsExactlyInAnyOrder(LlmRequest.TAG_PQ_QUESTION_BATCH,
                LlmRequest.TAG_PQ_QUESTION_FOLLOWUP_BATCH);
        assertThat(fake.requests().stream().filter(r -> LlmRequest.TAG_PQ_QUESTION_FOLLOWUP_BATCH.equals(r.callTag()))
                .findFirst().orElseThrow().messages().get(0).text())
                .contains("CONCERN:쉽게 피곤해요").contains("followup: 필요").contains("쉽게 피곤해요, 잠을 잘 못 자요");
        assertThat(turnLogIds(sessionId)).hasSize(5);
    }

    @Test
    void 묶음에서_걸린_주제와_이전_질문과_같은_질문은_단건으로_대신한다() throws Exception {
        properties.setPrefetchStrategy(PrefetchStrategy.BACKGROUND);
        // 묶음은 모든 질문이 같은 문장 → 첫 번째만 통과(나머지는 묶음 안 반복), 그 문장은 불편한 점 질문과도 같다
        fake.setResponder(req -> {
            if (LlmRequest.TAG_PQ_QUESTION_BATCH.equals(req.callTag())) {
                JsonNode out = fake.questionsOutput(req);
                out.get("questions").forEach(q -> ((ObjectNode) q).put("question", "같은 질문이에요 (최대 2개)"));
                return fake.structuredResponse(req, out);
            }
            JsonNode single = fake.questionOutput(req);
            if (single.get("answerType").asString().equals("MULTI")) {
                ((ObjectNode) single).put("question", "같은 질문이에요 (최대 2개)");
            }
            return fake.structuredResponse(req, single);
        });
        double repeated = counter("repeated");
        double invalid = counter("invalid");
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        while (s.get("question") != null && !s.get("question").isNull()) {
            s = data(answer(user, s, "MULTI".equals(s.at("/question/answerType").asString()) ? List.of(2) : List.of(1)));
        }

        // 불편한 점 + 단건 3개(약: 반복 검사, 질환·생활습관: 묶음에서 걸림) + 묶음 1
        assertThat(tags()).filteredOn(LlmRequest.TAG_PQ_QUESTION::equals).hasSize(4);
        assertThat(counter("repeated") - repeated).isEqualTo(1);
        assertThat(counter("invalid") - invalid).isEqualTo(2);
        assertThat(turnLogIds(sessionId)).doesNotContain(batchLogId(sessionId));
    }

    @Test
    void 회차가_끝나면_쓰지_않은_질문은_낭비로_센다() throws Exception {
        properties.setPrefetchStrategy(PrefetchStrategy.UPFRONT);
        double wasted = counter("wasted");
        User user = members.fullMale(1980);
        JsonNode s = data(start(user));
        long sessionId = s.get("sessionId").asLong();
        answer(user, s, List.of(2));
        mockMvc.perform(post(BASE + "/" + sessionId + "/close").header("X-User-Id", user.getId()))
                .andExpect(status().isOk());

        // 4개(불편한 점 + 프로필 3) 중 2개 사용(불편한 점, 약 1단계) → 2개 낭비
        assertThat(counter("wasted") - wasted).isEqualTo(2);
    }

    // ---------- helpers ----------

    private double counter(String result) {
        Counter c = meterRegistry.find("pq.prefetch").tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    private List<String> tags() {
        return fake.requests().stream().map(LlmRequest::callTag).toList();
    }

    private List<Long> turnLogIds(long sessionId) {
        return jdbcTemplate.queryForList("select llm_call_log_id from ai_question_turns where session_id = ?"
                + " and fixed = false order by turn_no", Long.class, sessionId);
    }

    private Long batchLogId(long sessionId) {
        return logId(sessionId, LlmRequest.TAG_PQ_QUESTION_BATCH);
    }

    private Long singleLogId(long sessionId) {
        return logId(sessionId, LlmRequest.TAG_PQ_QUESTION);
    }

    private Long logId(long sessionId, String tag) {
        return jdbcTemplate.queryForObject("select min(llm_call_log_id) from llm_call_logs where session_id = ?"
                + " and call_tag = ?", Long.class, sessionId, tag);
    }

    private ResultActions start(User user) throws Exception {
        return mockMvc.perform(post(BASE).header("X-User-Id", user.getId()));
    }

    private ResultActions answer(User user, JsonNode s, List<Integer> selected) throws Exception {
        return mockMvc.perform(post(BASE + "/" + s.get("sessionId").asLong() + "/answers").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("turnId", s.at("/question/turnId").asLong(),
                        "selected", selected))))
                .andExpect(status().isOk());
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
