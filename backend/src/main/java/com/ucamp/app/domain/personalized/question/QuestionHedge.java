package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.config.PersonalizedConfig;
import com.ucamp.app.domain.personalized.config.PersonalizedProperties;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 질문 단건 생성의 첫 시도에만 쓰는 이중 요청 (pq.hedge-enabled).
 * 첫 요청이 pq.hedge-after-ms 안에 오지 않으면 같은 요청을 PQ_QUESTION_HEDGE로 한 번 더 보내고,
 * 먼저 성공하고 검증까지 통과한 응답을 쓴다.
 * - 진 쪽은 끊지 않고 끝까지 받은 뒤 outcome = DISCARDED로 남긴다 (토큰 비용은 그대로 기록. 끊어도 비용은 돌려받지 못한다)
 * - 둘 다 검증에 실패하면 먼저 온 응답을 돌려준다 (호출하는 쪽이 그 응답으로 재요청 1회. 재요청에는 이중 요청 없음)
 * - 하나가 예외로 끝나면 다른 쪽을 기다린다. 둘 다 예외면 첫 요청의 예외를 던진다
 * - 재시도·서킷은 각 호출 안(ResilientLlmClient)에서 따로 동작한다. 서킷이 열려 있으면 첫 요청이 바로 예외라 이중 요청이 없다
 * 메트릭 pq.hedge{result}: fired(두 번째 요청을 보냄) / second_won(두 번째 응답을 씀)
 */
@Slf4j
@Component
public class QuestionHedge {
    private final LlmClient llmClient;
    private final LlmCallLogService llmCallLogService;
    private final PersonalizedProperties properties;
    private final TaskExecutor executor;
    private final MeterRegistry meterRegistry;

    public QuestionHedge(LlmClient llmClient, LlmCallLogService llmCallLogService, PersonalizedProperties properties,
                         @Qualifier(PersonalizedConfig.EXECUTOR) TaskExecutor executor, MeterRegistry meterRegistry) {
        this.llmClient = llmClient;
        this.llmCallLogService = llmCallLogService;
        this.properties = properties;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
    }

    private record Done(boolean hedge, LlmResponse response, RuntimeException error) {
    }

    /** valid = 검증 통과 여부. LLM 예외(LlmCallException, LlmUnavailableException)는 그대로 던진다 */
    public LlmResponse call(LlmRequest request, Predicate<LlmResponse> valid) {
        if (!properties.isHedgeEnabled()) {
            return llmClient.call(request);
        }
        BlockingQueue<Done> done = new LinkedBlockingQueue<>();
        if (!submit(request, false, done)) {
            return llmClient.call(request);
        }
        Done first = poll(done, properties.getHedgeAfterMs());
        if (first != null && (first.error() != null || valid.test(first.response()))) {
            return result(first);
        }
        // 첫 응답이 늦거나(first == null) 검증에 실패: 두 번째 요청
        boolean fired = first == null && submit(request.toBuilder().callTag(LlmRequest.TAG_PQ_QUESTION_HEDGE).build(),
                true, done);
        if (fired) {
            count("fired");
        }
        int pending = (first == null ? 1 : 0) + (fired ? 1 : 0);
        Done fallback = first;
        RuntimeException firstError = null;
        while (pending > 0) {
            Done d = poll(done, Long.MAX_VALUE);
            pending--;
            if (d.error() != null) {
                if (!d.hedge()) {
                    firstError = d.error();
                }
                continue;
            }
            if (valid.test(d.response())) {
                discardLater(done, pending);
                if (fallback != null) {
                    discard(fallback.response());
                }
                if (d.hedge()) {
                    count("second_won");
                }
                return d.response();
            }
            if (fallback == null) {
                fallback = d;
            } else {
                discard(d.response());
            }
        }
        if (fallback != null) {
            return fallback.response();
        }
        throw firstError != null ? firstError : new IllegalStateException("hedged question call finished without result");
    }

    private boolean submit(LlmRequest request, boolean hedge, BlockingQueue<Done> done) {
        try {
            executor.execute(() -> {
                try {
                    done.add(new Done(hedge, llmClient.call(request), null));
                } catch (RuntimeException e) {
                    done.add(new Done(hedge, null, e));
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("hedged question call rejected (executor full): tag={}", request.callTag());
            return false;
        }
    }

    private static Done poll(BlockingQueue<Done> done, long timeoutMs) {
        try {
            return done.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for question", e);
        }
    }

    private static LlmResponse result(Done d) {
        if (d.error() != null) {
            throw d.error();
        }
        return d.response();
    }

    // 아직 오지 않은 쪽은 받는 대로 DISCARDED로 남긴다 (요청 스레드는 기다리지 않는다)
    private void discardLater(BlockingQueue<Done> done, int pending) {
        if (pending <= 0) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < pending; i++) {
                Done d = poll(done, Long.MAX_VALUE);
                if (d.response() != null) {
                    discard(d.response());
                }
            }
        }, executor);
    }

    private void discard(LlmResponse response) {
        if (response != null && response.callLogId() != null) {
            llmCallLogService.markOutcome(response.callLogId(), LlmCallLog.DISCARDED);
        }
    }

    private void count(String result) {
        meterRegistry.counter("pq.hedge", "result", result).increment();
    }
}
