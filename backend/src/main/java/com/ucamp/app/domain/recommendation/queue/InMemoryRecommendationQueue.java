package com.ucamp.app.domain.recommendation.queue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// 메모리 큐 (기본값·테스트용). 서버 한 대 안에서만 동작하고 재시작하면 비워진다 (DB의 PENDING 행으로 복구)
public class InMemoryRecommendationQueue implements RecommendationQueue {
    private final LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(long recommendationId) {
        queue.add(recommendationId);
    }

    @Override
    public Optional<Long> poll(Duration timeout) throws InterruptedException {
        return Optional.ofNullable(queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public long size() {
        return queue.size();
    }
}
