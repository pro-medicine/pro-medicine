package com.ucamp.app.infra.llm;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 장애 대응 데코레이터 (Resilience4j). 순서: Retry → CircuitBreaker → Bulkhead → (LoggingLlmClient → HTTP).
 * - 재시도: 429, 5xx, 타임아웃, 네트워크 오류만. 400·401 등 4xx는 재시도하지 않는다. 지수 백오프 + 지터, 429의 retry-after를 따른다
 * - 시도마다 attempt_no(1, 2, 3 …)를 요청에 실어 llm_call_logs에 남긴다 (출력 검증 재요청과는 별개)
 * - 서킷 브레이커: 재시도 대상 실패만 실패로 센다. OPEN이면 바로 LlmUnavailableException(CIRCUIT_OPEN)
 * - 재시도 대상 실패가 끝까지 이어지면 LlmUnavailableException(RETRY_EXHAUSTED) → 호출한 쪽에서 규칙 기반 폴백
 * 타임아웃은 HTTP 클라이언트의 읽기 타임아웃(llm.timeout-ms, 켤 때 권장 15000)으로 둔다.
 */
public class ResilientLlmClient implements LlmClient {
    /** Resilience4j 레지스트리에 등록하는 이름 (서킷·재시도·Bulkhead 공통) */
    public static final String RESILIENCE_NAME = "llm";

    private final LlmClient delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    private ResilientLlmClient(LlmClient delegate, Retry retry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    /** llm.resilience.* 설정으로 재시도·서킷 브레이커·Bulkhead를 만들어 감싼다 */
    public static ResilientLlmClient create(LlmClient delegate, LlmProperties.Resilience r,
                                            CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry,
                                            BulkheadRegistry bulkheadRegistry, MeterRegistry meterRegistry) {
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker(RESILIENCE_NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(r.getCircuitWindowSize())
                .minimumNumberOfCalls(r.getCircuitMinimumCalls())
                .failureRateThreshold(r.getCircuitFailureRateThreshold())
                .waitDurationInOpenState(r.getCircuitOpenWait())
                .permittedNumberOfCallsInHalfOpenState(r.getCircuitHalfOpenCalls())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(ResilientLlmClient::isRetryable)
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(e -> meterRegistry.counter("recommendation.circuit.transition",
                "to", e.getStateTransition().getToState().name()).increment());
        Retry retry = retryRegistry.retry(RESILIENCE_NAME, retryConfig(r));
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(RESILIENCE_NAME, BulkheadConfig.custom()
                .maxConcurrentCalls(r.getBulkheadMaxConcurrent())
                .maxWaitDuration(r.getBulkheadMaxWait())
                .build());
        return new ResilientLlmClient(delegate, retry, circuitBreaker, bulkhead);
    }

    /** 재시도 설정: 재시도 대상 판단과 대기 시간(429 retry-after 우선) */
    private static RetryConfig retryConfig(LlmProperties.Resilience r) {
        IntervalFunction backoff = IntervalFunction.ofExponentialRandomBackoff(
                r.getInitialBackoff().toMillis(), r.getBackoffMultiplier(), r.getJitter());
        IntervalBiFunction<Object> interval = (attempt, either) -> {
            if (either.isLeft() && either.getLeft() instanceof LlmCallException e && e.getRetryAfterSeconds() != null) {
                return Math.min(e.getRetryAfterSeconds() * 1000, r.getMaxRetryAfter().toMillis());
            }
            return backoff.apply(attempt);
        };
        return RetryConfig.custom()
                .maxAttempts(r.getMaxAttempts())
                .intervalBiFunction(interval)
                .retryOnException(ResilientLlmClient::isRetryable)
                .failAfterMaxAttempts(false)
                .build();
    }

    // 서킷 브레이커의 실패 집계와 재시도 대상 판단에 같이 쓴다 (429·5xx·타임아웃·네트워크)
    private static boolean isRetryable(Throwable t) {
        return t instanceof LlmCallException e && e.isRetryable();
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        AtomicInteger attempt = new AtomicInteger();
        try {
            return retry.executeSupplier(() -> {
                LlmRequest attemptRequest = request.toBuilder().attemptNo(attempt.incrementAndGet()).build();
                return circuitBreaker.executeSupplier(() ->
                        bulkhead.executeSupplier(() -> delegate.call(attemptRequest)));
            });
        } catch (CallNotPermittedException e) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.CIRCUIT_OPEN, e);
        } catch (BulkheadFullException e) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.BULKHEAD_FULL, e);
        } catch (LlmCallException e) {
            if (e.isRetryable()) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.RETRY_EXHAUSTED, e);
            }
            throw e;
        }
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }
}
