package com.ucamp.app.domain.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.support.PerfStats;
import com.ucamp.app.support.TestUsers;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 02 측정: 설문 시작/제출 API 지연(p50/p95)과 설문 시작 시 SQL 수.
 * 실행: ./gradlew perfTest -PperfLabel=after  (결과: docs/perf/runs/STEP02-survey-{label}.json)
 * 조건: 로컬 JVM, H2 인메모리, MockMvc(HTTP 소켓 없음). 각 220회 호출, 앞 20회는 워밍업으로 제외.
 */
@Tag("perf")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class SurveyApiPerfTest {
    private static final int WARMUP = 20;
    private static final int MEASURED = 200;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    HealthProfileRepository healthProfileRepository;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void measure() throws Exception {
        User member = TestUsers.create(userRepository, true);
        // 제출 한 번으로 프로필·질환·복용 약이 있는 상태(미리 채우기 대상)를 만든다
        mockMvc.perform(submitRequest(member.getId())).andExpect(status().isOk());

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", System.getProperty("perf.label", "run"));
        result.put("date", LocalDateTime.now().toString());
        result.put("conditions", "local JVM, H2 in-memory (MySQL mode), MockMvc, warmup " + WARMUP + ", measured " + MEASURED);

        result.put("sql_count_start_member", sqlCount(stats, activeRequest(member.getId())));
        result.put("sql_count_start_guest", sqlCount(stats, activeRequest(null)));
        result.put("sql_count_submit_member", sqlCount(stats, submitRequest(member.getId())));

        result.put("start_member", measure(activeRequest(member.getId())).summary());
        result.put("start_guest", measure(activeRequest(null)).summary());
        result.put("submit_member", measure(submitRequest(member.getId())).summary());
        result.put("submit_guest", measure(submitGuestRequest()).summary());

        String json = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(result);
        Path out = Path.of("docs/perf/runs/STEP02-survey-" + result.get("label") + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json);
        System.out.println("[STEP02-PERF] " + json);
    }

    private long sqlCount(Statistics stats, RequestBuilder request) throws Exception {
        stats.clear();
        mockMvc.perform(request).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }

    private PerfStats measure(RequestBuilder request) throws Exception {
        PerfStats perf = new PerfStats();
        for (int i = 0; i < WARMUP + MEASURED; i++) {
            long start = System.nanoTime();
            mockMvc.perform(request).andExpect(status().isOk());
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            if (i >= WARMUP) {
                perf.add(ms);
            }
        }
        return perf;
    }

    private RequestBuilder activeRequest(Long userId) {
        var req = get("/api/v1/surveys/active");
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        return req;
    }

    // 회원이 14문항 전부 답한 제출
    private RequestBuilder submitRequest(Long userId) throws Exception {
        List<Map<String, Object>> answers = List.of(
                choice(Q_GENDER, GENDER_M), number(Q_BIRTH, 1990), choice(Q_SMOKING, SMOKING_HALF_PACK),
                choice(Q_DRINKING, DRINKING_HIGH), choice(Q_CAFFEINE, CAFFEINE_HIGH), choice(Q_DIET, DIET_OMNI),
                choice(Q_SUN, SUN_LOW), choice(Q_SLEEP, SLEEP_POOR), choice(Q_FATIGUE, FATIGUE_HIGH),
                choice(Q_GOAL, GOAL_SLEEP, GOAL_IMMUNITY), choice(Q_PREGNANCY, PREGNANCY_NA),
                choice(Q_CONDITIONS, CONDITION_HYPERTENSION),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-003\"},{\"name\":\"기타 약\"}]"),
                text(Q_SUPPLEMENTS, "[{\"ingredient\":\"MAGNESIUM\",\"amount\":30,\"unit\":\"mg\"}]"));
        return post("/api/v1/surveys/" + SURVEY_ID + "/responses").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
    }

    // 비회원이 볼 수 있는 11문항 전부 답한 제출
    private RequestBuilder submitGuestRequest() throws Exception {
        List<Map<String, Object>> answers = List.of(
                choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995), choice(Q_SMOKING, SMOKING_NONE),
                choice(Q_DRINKING, DRINKING_NONE), choice(Q_CAFFEINE, CAFFEINE_NONE), choice(Q_DIET, DIET_VEGAN),
                choice(Q_SUN, SUN_NORMAL), choice(Q_SLEEP, SLEEP_POOR), choice(Q_FATIGUE, FATIGUE_HIGH),
                choice(Q_GOAL, GOAL_SLEEP), text(Q_SUPPLEMENTS, "[]"));
        return post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
    }
}
