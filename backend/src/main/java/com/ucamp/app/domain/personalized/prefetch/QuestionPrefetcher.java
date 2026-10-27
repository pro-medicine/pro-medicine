package com.ucamp.app.domain.personalized.prefetch;

import com.ucamp.app.domain.personalized.config.PersonalizedConfig;
import com.ucamp.app.domain.personalized.config.PersonalizedProperties;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.question.GeneratedQuestion;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator;
import com.ucamp.app.domain.personalized.question.QuestionBatchGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 미리 만든 맞춤 질문 보관 (회차 단위, 메모리). 서버가 재시작되면 잃지만 그때는 단건 생성으로 대신한다.
 * - submit: 묶음 생성을 등록 (백그라운드 또는 지금 바로). 등록한 주제는 take에서 결과를 기다릴 수 있다
 * - take: 서버가 고른 주제의 질문이 있으면 꺼낸다. 생성 중이면 pq.prefetch-wait-ms까지 기다리고, 넘으면 empty (단건 생성)
 *   미리 만든 질문은 이전 답을 모르고 만들어졌으므로 꺼내기 직전에 이 회차의 이전 질문과 반복 검사를 다시 한다
 * - finish: 회차가 끝나면(READY·ABANDONED·FAILED) 비우고, 쓰지 않은 질문 수를 낭비로 센다
 * 메트릭 pq.prefetch{result}: created(통과해서 보관) / hit(사용) / wasted(버림) / timeout·failed·invalid·repeated(단건으로 대신)
 */
@Slf4j
@Component
public class QuestionPrefetcher {
    /** 끝을 알리지 않은 회차(창을 그냥 닫은 경우 등)는 이 시간이 지나면 다음 등록 때 정리한다 */
    static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final QuestionBatchGenerator batchGenerator;
    private final PersonalizedProperties properties;
    private final TaskExecutor executor;
    private final MeterRegistry meterRegistry;
    private final Map<Long, SessionPrefetch> sessions = new ConcurrentHashMap<>();

    public QuestionPrefetcher(QuestionBatchGenerator batchGenerator, PersonalizedProperties properties,
                              @Qualifier(PersonalizedConfig.EXECUTOR) TaskExecutor executor, MeterRegistry meterRegistry) {
        this.batchGenerator = batchGenerator;
        this.properties = properties;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
    }

    private static final class SessionPrefetch {
        final long createdAt = System.nanoTime();
        final Map<String, CompletableFuture<QuestionBatchGenerator.Result>> byKey = new ConcurrentHashMap<>();
        final Set<String> taken = ConcurrentHashMap.newKeySet();
        final AtomicInteger hits = new AtomicInteger();
    }

    public PrefetchStrategy strategy() {
        return properties.getPrefetchStrategy();
    }

    /**
     * 묶음 생성 등록. async = false면 지금 스레드에서 끝까지 만든다 (방식 ②의 회차 시작).
     * 이미 등록된 주제는 다시 만들지 않는다. 실행기가 가득 차서 거절되면 등록하지 않는다 (그 주제는 단건 생성)
     */
    public void submit(QuestionBatchGenerator.Request request, boolean async) {
        evictStale();
        SessionPrefetch sp = sessions.computeIfAbsent(request.sessionId(), id -> new SessionPrefetch());
        var items = request.items().stream()
                .filter(i -> !sp.byKey.containsKey(QuestionBatchGenerator.key(i.topic(), 1)))
                .toList();
        if (items.isEmpty()) {
            return;
        }
        QuestionBatchGenerator.Request req = new QuestionBatchGenerator.Request(request.sessionId(), request.known(),
                items, request.history(), request.callTag());
        CompletableFuture<QuestionBatchGenerator.Result> future = new CompletableFuture<>();
        // 실행 전에 등록해 둔다 (백그라운드 생성 중에 온 take도 기다릴 수 있게)
        for (QuestionBatchGenerator.Item i : items) {
            sp.byKey.put(QuestionBatchGenerator.key(i.topic(), 1), future);
            if (i.followup()) {
                sp.byKey.put(QuestionBatchGenerator.key(i.topic(), 2), future);
            }
        }
        if (!async) {
            run(req, future);
            return;
        }
        try {
            executor.execute(() -> run(req, future));
        } catch (RejectedExecutionException e) {
            log.warn("question prefetch rejected (executor full): session={}", req.sessionId());
            sp.byKey.values().removeIf(f -> f == future);
        }
    }

    private void run(QuestionBatchGenerator.Request req, CompletableFuture<QuestionBatchGenerator.Result> future) {
        try {
            QuestionBatchGenerator.Result result = batchGenerator.generate(req);
            count("created", result.questions().size());
            future.complete(result);
        } catch (RuntimeException e) {
            log.warn("question prefetch failed: session={}, tag={}, error={}", req.sessionId(), req.callTag(),
                    e.getMessage());
            future.completeExceptionally(e);
        }
    }

    /** 서버가 고른 주제의 미리 만든 질문. 없거나 쓸 수 없으면 empty → 호출하는 쪽이 단건 생성 */
    public Optional<GeneratedQuestion> take(Long sessionId, Topic topic, int depth, Collection<String> previousQuestions) {
        SessionPrefetch sp = sessions.get(sessionId);
        String key = QuestionBatchGenerator.key(topic, depth);
        CompletableFuture<QuestionBatchGenerator.Result> future = sp == null ? null : sp.byKey.get(key);
        if (future == null || !sp.taken.add(key)) {
            return Optional.empty();
        }
        QuestionBatchGenerator.Result result;
        try {
            result = future.get(properties.getPrefetchWaitMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            count("timeout", 1);
            return Optional.empty();
        } catch (ExecutionException e) {
            count("failed", 1);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        GeneratedQuestion q = result.questions().get(key);
        if (q == null) {
            count("invalid", 1);
            return Optional.empty();
        }
        if (PersonalizedQuestionValidator.isRepeated(q.question(), previousQuestions)) {
            count("repeated", 1);
            return Optional.empty();
        }
        sp.hits.incrementAndGet();
        count("hit", 1);
        return Optional.of(q);
    }

    /** 회차 끝: 쓰지 않은 질문은 낭비로 세고 비운다. 아직 생성 중인 묶음은 결과를 버린다 (취소는 하지 않음: 비용은 이미 든다) */
    public void finish(Long sessionId) {
        SessionPrefetch sp = sessions.remove(sessionId);
        if (sp == null) {
            return;
        }
        Map<CompletableFuture<QuestionBatchGenerator.Result>, Boolean> distinct = new IdentityHashMap<>();
        sp.byKey.values().forEach(f -> distinct.put(f, true));
        int created = 0;
        for (CompletableFuture<QuestionBatchGenerator.Result> f : distinct.keySet()) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                created += f.join().questions().size();
            }
        }
        count("wasted", Math.max(0, created - sp.hits.get()));
    }

    private void evictStale() {
        long now = System.nanoTime();
        sessions.entrySet().stream()
                .filter(e -> now - e.getValue().createdAt > STALE_AFTER.toNanos())
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::finish);
    }

    private void count(String result, int amount) {
        if (amount > 0) {
            meterRegistry.counter("pq.prefetch", "result", result).increment(amount);
        }
    }
}
