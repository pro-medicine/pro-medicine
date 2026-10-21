package com.ucamp.app.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.support.PerfStats;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 09 2-2: 알림을 열고 들어온 회원의 LLM 호출 절감.
 * 회원 200명이 모두 추천을 받은 상태에서, 가정: 30%(60명)는 그 뒤 프로필이 바뀌었다.
 * 알림 진입 흐름 = GET /users/me/recommendations/latest → 재사용 가능하면 끝, 아니면 새 설문 → 새 추천.
 * 비교: 재사용 없이 모두 새로 만들면 LLM 200회. 절감률은 "프로필 변경 비율" 가정에 그대로 따라간다.
 * 실행: ./gradlew perfTest --tests '*NotificationReusePerfTest*'
 */
@Tag("perf")
@SpringBootTest(properties = "recommendation.pipeline-version=V4")
@AutoConfigureMockMvc
class NotificationReusePerfTest {
    static final int MEMBERS = 200;
    static final double CHANGED_SHARE = 0.3;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    FakeLlmClient fake;

    @Test
    void measure() throws Exception {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            User u = TestUsers.create(userRepository, true);
            users.add(u);
            recommend(u.getId());
        }
        int changed = (int) (MEMBERS * CHANGED_SHARE);
        for (int i = 0; i < changed; i++) {
            jdbcTemplate.update("update health_profiles set updated_at = ? where user_id = ?",
                    LocalDateTime.now().plusMinutes(1), users.get(i).getId());
        }
        fake.reset();

        PerfStats latestLatency = new PerfStats();
        int reused = 0;
        for (User u : users) {
            long s = System.nanoTime();
            JsonNode latest = data(mockMvc.perform(get("/api/v1/users/me/recommendations/latest")
                    .header("X-User-Id", u.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            latestLatency.add((System.nanoTime() - s) / 1_000_000.0);
            if (latest.get("reusable").asBoolean()) {
                reused++;
            } else {
                recommend(u.getId());
            }
        }
        int llmCalls = fake.requests().size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conditions", "MockMvc, H2, Fake LLM(호출 수만 셈), members=" + MEMBERS + ", 가정: 추천 후 프로필 변경 비율 "
                + CHANGED_SHARE);
        out.put("members", MEMBERS);
        out.put("reused", reused);
        out.put("llmCallsWithReuse", llmCalls);
        out.put("llmCallsWithoutReuse", MEMBERS);
        out.put("llmCallReduction", 1.0 - (double) llmCalls / MEMBERS);
        out.put("latestApiLatency", latestLatency.summary());
        Path json = Path.of("docs/perf/runs/STEP09-notification-reuse-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(json.getParent());
        Files.writeString(json, om.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("[REUSE] " + om.writeValueAsString(out));
    }

    private void recommend(long userId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("answers", List.of(choice(Q_GENDER, GENDER_M),
                                choice(Q_SUN, SUN_LOW), choice(Q_CONDITIONS, CONDITION_NONE))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = data(body).get("responseId").asLong();
        mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":" + responseId + "}")).andExpect(status().isOk());
    }

    private JsonNode data(String body) throws Exception {
        return om.readTree(body).get("data");
    }
}
