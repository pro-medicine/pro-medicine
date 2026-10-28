package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.config.PersonalizedProperties;
import com.ucamp.app.domain.personalized.question.QuestionHedge;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// 4번: 질문 첫 시도가 늦으면 같은 요청을 한 번 더 보내고 먼저 온 검증 통과 응답을 쓴다
class QuestionHedgeTest {
    /** 태그별 응답 지연(ms)과 결과. "valid" / "invalid" / "timeout" */
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final Map<String, String> results = new ConcurrentHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final LlmCallLogService logService = mock(LlmCallLogService.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PersonalizedProperties properties = new PersonalizedProperties();
    private ThreadPoolTaskExecutor executor;
    private QuestionHedge hedge;

    private final LlmClient client = request -> {
        String tag = request.callTag();
        calls.add(tag);
        sleep(delays.getOrDefault(tag, 0L));
        String result = results.getOrDefault(tag, "valid");
        if ("timeout".equals(result)) {
            throw new LlmCallException(LlmCallException.Kind.TIMEOUT, null, null, 0, "timeout", null);
        }
        return LlmResponse.builder().text(result).callLogId(LlmRequest.TAG_PQ_QUESTION.equals(tag) ? 1L : 2L)
                .latencyMs(0).build();
    };

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.initialize();
        properties.setHedgeEnabled(true);
        properties.setHedgeAfterMs(100);
        hedge = new QuestionHedge(client, logService, properties, executor, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void 첫_응답이_빠르면_이중_요청을_하지_않는다() {
        LlmResponse r = hedge.call(request(), this::valid);

        assertThat(r.callLogId()).isEqualTo(1L);
        assertThat(calls).containsExactly(LlmRequest.TAG_PQ_QUESTION);
        assertThat(meterRegistry.find("pq.hedge").counter()).isNull();
    }

    @Test
    void 첫_응답이_늦으면_두_번째_요청의_응답을_쓰고_늦은_쪽은_DISCARDED() {
        delays.put(LlmRequest.TAG_PQ_QUESTION, 600L);

        LlmResponse r = hedge.call(request(), this::valid);

        assertThat(r.callLogId()).isEqualTo(2L);
        assertThat(calls).containsExactly(LlmRequest.TAG_PQ_QUESTION, LlmRequest.TAG_PQ_QUESTION_HEDGE);
        assertThat(meterRegistry.counter("pq.hedge", "result", "fired").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("pq.hedge", "result", "second_won").count()).isEqualTo(1);
        verify(logService, timeout(2000)).markOutcome(1L, LlmCallLog.DISCARDED);
    }

    @Test
    void 이중_요청을_보냈어도_첫_응답이_먼저_오면_첫_응답() {
        delays.put(LlmRequest.TAG_PQ_QUESTION, 200L);
        delays.put(LlmRequest.TAG_PQ_QUESTION_HEDGE, 1000L);

        LlmResponse r = hedge.call(request(), this::valid);

        assertThat(r.callLogId()).isEqualTo(1L);
        assertThat(meterRegistry.counter("pq.hedge", "result", "second_won").count()).isZero();
        verify(logService, timeout(3000)).markOutcome(2L, LlmCallLog.DISCARDED);
    }

    @Test
    void 둘_다_타임아웃이면_첫_요청의_예외() {
        delays.put(LlmRequest.TAG_PQ_QUESTION, 300L);
        results.put(LlmRequest.TAG_PQ_QUESTION, "timeout");
        results.put(LlmRequest.TAG_PQ_QUESTION_HEDGE, "timeout");

        assertThatThrownBy(() -> hedge.call(request(), this::valid))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.getKind()).isEqualTo(LlmCallException.Kind.TIMEOUT));
        assertThat(calls).hasSize(2);
    }

    @Test
    void 늦은_첫_응답이_검증_실패면_두_번째를_기다려_쓴다() {
        delays.put(LlmRequest.TAG_PQ_QUESTION, 200L);
        delays.put(LlmRequest.TAG_PQ_QUESTION_HEDGE, 400L);
        results.put(LlmRequest.TAG_PQ_QUESTION, "invalid");

        LlmResponse r = hedge.call(request(), this::valid);

        assertThat(r.callLogId()).isEqualTo(2L);
        verify(logService).markOutcome(1L, LlmCallLog.DISCARDED);
    }

    @Test
    void 빠른_첫_응답이_검증_실패면_이중_요청_없이_그대로_돌려준다_재요청은_호출하는_쪽() {
        results.put(LlmRequest.TAG_PQ_QUESTION, "invalid");

        LlmResponse r = hedge.call(request(), this::valid);

        assertThat(r.text()).isEqualTo("invalid");
        assertThat(calls).containsExactly(LlmRequest.TAG_PQ_QUESTION);
        verify(logService, never()).markOutcome(1L, LlmCallLog.DISCARDED);
    }

    @Test
    void 꺼져_있으면_바로_호출() {
        properties.setHedgeEnabled(false);
        delays.put(LlmRequest.TAG_PQ_QUESTION, 300L);

        hedge.call(request(), this::valid);

        assertThat(calls).containsExactly(LlmRequest.TAG_PQ_QUESTION);
    }

    private boolean valid(LlmResponse r) {
        return "valid".equals(r.text());
    }

    private static LlmRequest request() {
        return LlmRequest.builder().callTag(LlmRequest.TAG_PQ_QUESTION).messages(List.of()).build();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
