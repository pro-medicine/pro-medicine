package com.ucamp.app.domain.recommendation.queue;

import com.ucamp.app.domain.recommendation.service.RecommendationProperties;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 큐 워커 (recommendation.api-mode=queue일 때만 동작). 스레드 수 = recommendation.worker.concurrency = LLM 동시 호출 상한.
 * 시작할 때 DB의 PENDING 행을 큐에 다시 넣는다 (재시작·큐 유실 복구).
 * Gauge: recommendation.worker.active(처리 중), recommendation.queue.size(대기)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationWorker implements SmartLifecycle {
    private final RecommendationQueue queue;
    private final RecommendationService recommendationService;
    private final RecommendationStore store;
    private final RecommendationProperties properties;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger active = new AtomicInteger();
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    @Override
    public void start() {
        if (!properties.isQueueMode()) {
            return;
        }
        Gauge.builder("recommendation.worker.active", active, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("recommendation.queue.size", queue, RecommendationQueue::size).register(meterRegistry);
        List<Long> pending = store.pendingIds();
        pending.forEach(queue::enqueue);
        running = true;
        int concurrency = properties.getWorker().getConcurrency();
        for (int i = 0; i < concurrency; i++) {
            Thread t = new Thread(this::loop, "rec-worker-" + i);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        log.info("recommendation worker started: concurrency={}, recovered pending={}", concurrency, pending.size());
    }

    private void loop() {
        while (running) {
            try {
                queue.poll(properties.getWorker().getPollTimeout()).ifPresent(id -> {
                    active.incrementAndGet();
                    try {
                        recommendationService.process(id);
                    } finally {
                        active.decrementAndGet();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("worker loop error: {}", e.getClass().getSimpleName());
            }
        }
    }

    public int activeCount() {
        return active.get();
    }

    @Override
    public void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
