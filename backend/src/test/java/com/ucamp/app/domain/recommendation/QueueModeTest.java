package com.ucamp.app.domain.recommendation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.support.InMemoryLlmRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// V9-queue: 202 접수 → 워커 처리 → GET 상태 조회, 멱등, 실패 처리. 워커가 별도 스레드라 트랜잭션 롤백을 쓰지 않는다
@SpringBootTest(properties = {"recommendation.api-mode=queue", "recommendation.worker.concurrency=2",
        "recommendation.worker.poll-timeout=100ms", "recommendation.pipeline-version=V4"})
@AutoConfigureMockMvc
@Import(QueueModeTest.RateLimitConfig.class)
class QueueModeTest {

    @TestConfiguration
    static class RateLimitConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        InMemoryLlmRateLimiter inMemoryLlmRateLimiter() {
            return new InMemoryLlmRateLimiter();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    InMemoryLlmRateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 접수는_202_PENDING_워커가_처리하면_DONE() throws Exception {
        long responseId = submitSurvey();

        JsonNode accepted = create(responseId, 202);
        assertThat(accepted.get("status").asString()).isIn("PENDING", "PROCESSING");

        JsonNode done = waitUntilFinished(accepted.get("recommendationId").asLong());
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        assertThat(done.get("items").size()).isPositive();
    }

    @Test
    void 같은_responseId_중복_요청은_기존_작업을_돌려준다_동시_요청_포함() throws Exception {
        long responseId = submitSurvey();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<Long> call = () -> {
                // 게스트 inflight 잠금으로 동시 요청이 429를 받을 수 있으므로 재시도한다
                for (int attempt = 0; attempt < 20; attempt++) {
                    var req = post("/api/v1/recommendations")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"responseId\":" + responseId + "}");
                    if (lastGuestToken != null) {
                        req.header("X-Guest-Token", lastGuestToken);
                    }
                    var result = mockMvc.perform(req).andReturn();
                    if (result.getResponse().getStatus() == 429) {
                        Thread.sleep(50);
                        continue;
                    }
                    return body(result.getResponse().getContentAsString()).get("recommendationId").asLong();
                }
                throw new AssertionError("8번 재시도해도 429");
            };
            futures.add(pool.submit(call));
        }
        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        long id = ids.iterator().next();
        waitUntilFinished(id);
        // 끝난 뒤 다시 요청해도 같은 결과 (200, DONE)
        assertThat(create(responseId, 200).get("recommendationId").asLong()).isEqualTo(id);
    }

    @Test
    void LLM_실패는_FAILED와_error_code() throws Exception {
        fake.setResponder(req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, 500, null, 1, "x", null);
        });
        long responseId = submitSurvey();

        JsonNode done = waitUntilFinished(create(responseId, 202).get("recommendationId").asLong());

        assertThat(done.get("status").asString()).isEqualTo("FAILED");
        assertThat(done.get("errorCode").asString()).isEqualTo("R003");
    }

    private String lastGuestToken;

    private long submitSurvey() throws Exception {
        String body = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("answers", List.of(choice(Q_GENDER, GENDER_M),
                                choice(Q_SUN, SUN_LOW))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = body(body);
        if (data.has("guestToken")) {
            lastGuestToken = data.get("guestToken").asString();
        }
        return data.get("responseId").asLong();
    }

    private JsonNode create(long responseId, int expectedStatus) throws Exception {
        var req = post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":" + responseId + "}");
        if (lastGuestToken != null) {
            req.header("X-Guest-Token", lastGuestToken);
        }
        return body(mockMvc.perform(req)
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode waitUntilFinished(long recommendationId) throws Exception {
        for (int i = 0; i < 100; i++) {
            var req = get("/api/v1/recommendations/" + recommendationId);
            if (lastGuestToken != null) {
                req.header("X-Guest-Token", lastGuestToken);
            }
            JsonNode data = body(mockMvc.perform(req)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String s = data.get("status").asString();
            if ("DONE".equals(s) || "FAILED".equals(s)) {
                return data;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("not finished: " + recommendationId);
    }

    private JsonNode body(String json) throws Exception {
        return om.readTree(json).get("data");
    }
}
