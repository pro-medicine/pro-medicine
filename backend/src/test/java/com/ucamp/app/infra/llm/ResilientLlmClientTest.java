package com.ucamp.app.infra.llm;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// STEP 10: 재시도 대상/비대상 상태 코드, 서킷 상태 전이, 재시도 소진
class ResilientLlmClientTest {

    private static LlmProperties.Resilience config(int maxAttempts, int window) {
        LlmProperties.Resilience r = new LlmProperties.Resilience();
        r.setEnabled(true);
        r.setMaxAttempts(maxAttempts);
        r.setInitialBackoff(Duration.ofMillis(10));
        r.setMaxRetryAfter(Duration.ofMillis(50));
        r.setCircuitWindowSize(window);
        r.setCircuitMinimumCalls(window);
        r.setCircuitOpenWait(Duration.ofMillis(300));
        r.setCircuitHalfOpenCalls(2);
        r.setBulkheadMaxConcurrent(5);
        return r;
    }

    private static ResilientLlmClient client(LlmClient delegate, LlmProperties.Resilience r) {
        return LlmConfig.resilient(delegate, r, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(), new SimpleMeterRegistry());
    }

    /** 응답 순서를 정해 두는 가짜 클라이언트. 받은 attempt_no를 기록한다 */
    static class Scripted implements LlmClient {
        final List<Function<LlmRequest, LlmResponse>> script = new ArrayList<>();
        final List<Integer> attempts = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        Scripted then(Function<LlmRequest, LlmResponse> f) {
            script.add(f);
            return this;
        }

        @Override
        public LlmResponse call(LlmRequest request) {
            attempts.add(request.attemptNoOrDefault());
            int i = calls.getAndIncrement();
            return script.get(Math.min(i, script.size() - 1)).apply(request);
        }
    }

    static Function<LlmRequest, LlmResponse> http(int status, Long retryAfter) {
        return req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, status, retryAfter, 1, "x", null);
        };
    }

    static Function<LlmRequest, LlmResponse> timeout() {
        return req -> {
            throw new LlmCallException(LlmCallException.Kind.TIMEOUT, null, null, 1, "t", null);
        };
    }

    static Function<LlmRequest, LlmResponse> ok() {
        return req -> LlmResponse.builder().text("ok").model("m").build();
    }

    private static LlmRequest request() {
        return LlmRequest.builder().model("m").messages(List.of(LlmMessage.user("u"))).maxTokens(10).build();
    }

    @Test
    void 재시도_대상_5xx_429_타임아웃은_재시도하고_attempt_no가_늘어난다() {
        Scripted s = new Scripted().then(http(503, null)).then(http(429, 1L)).then(ok());

        LlmResponse r = client(s, config(3, 100)).call(request());

        assertThat(r.text()).isEqualTo("ok");
        assertThat(s.attempts).containsExactly(1, 2, 3);

        Scripted t = new Scripted().then(timeout()).then(ok());
        client(t, config(3, 100)).call(request());
        assertThat(t.calls.get()).isEqualTo(2);
    }

    @Test
    void 재시도_비대상_400_401은_한_번만_호출하고_그대로_실패() {
        for (int status : new int[]{400, 401}) {
            Scripted s = new Scripted().then(http(status, null));

            assertThatThrownBy(() -> client(s, config(3, 100)).call(request()))
                    .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
            assertThat(s.calls.get()).isEqualTo(1);
        }
    }

    @Test
    void 재시도_소진은_RETRY_EXHAUSTED() {
        Scripted s = new Scripted().then(http(500, null));

        assertThatThrownBy(() -> client(s, config(3, 100)).call(request()))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(LlmUnavailableException.Reason.RETRY_EXHAUSTED));
        assertThat(s.calls.get()).isEqualTo(3);
    }

    @Test
    void 서킷_CLOSED_OPEN_HALF_OPEN_CLOSED_전이() throws Exception {
        Scripted s = new Scripted();
        AtomicInteger mode = new AtomicInteger(0); // 0 = 실패, 1 = 성공
        s.then(req -> mode.get() == 0 ? http(500, null).apply(req) : ok().apply(req));
        ResilientLlmClient c = client(s, config(1, 4));
        CircuitBreaker cb = c.circuitBreaker();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> c.call(request())).isInstanceOf(LlmUnavailableException.class);
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = s.calls.get();
        assertThatThrownBy(() -> c.call(request()))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(LlmUnavailableException.Reason.CIRCUIT_OPEN));
        assertThat(s.calls.get()).isEqualTo(before); // OPEN이면 호출하지 않는다

        Thread.sleep(400);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        mode.set(1);
        c.call(request());
        c.call(request());
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void _4xx는_서킷_실패로_세지_않는다() {
        Scripted s = new Scripted().then(http(400, null));
        ResilientLlmClient c = client(s, config(1, 4));

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> c.call(request())).isInstanceOf(LlmCallException.class);
        }
        assertThat(c.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
