package com.ucamp.app.domain.recommendation.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 추천 작업 큐 (V9-queue). 값은 ai_recommendations.recommendation_id.
 * 구현: Redis List(recommendation.queue.store=redis, LPUSH/BRPOP) / 메모리(기본, 테스트).
 * 작업 상태의 원본은 DB(ai_recommendations.status)라 큐에서 유실돼도 PENDING 행으로 다시 넣을 수 있다.
 */
public interface RecommendationQueue {
    void enqueue(long recommendationId);

    /** 최대 timeout 동안 기다렸다가 하나 꺼낸다 */
    Optional<Long> poll(Duration timeout) throws InterruptedException;

    long size();
}
