package com.ucamp.app.domain.recommendation.queue;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis List 큐: LPUSH로 넣고 BRPOP으로 꺼낸다 (FIFO).
 * Stream 대신 List를 고른 이유: 작업 상태를 DB(ai_recommendations.status)가 갖고 있어 Stream의 consumer group·ack가
 * 없어도 PENDING/PROCESSING 행으로 재처리할 수 있고, 운영할 것이 적다. 다만 꺼낸 뒤 워커가 죽으면 그 작업은
 * 큐에서 사라지므로 서버 시작 시 PENDING 행을 다시 넣는다 (RecommendationWorker).
 */
public class RedisRecommendationQueue implements RecommendationQueue {
    static final String KEY = "rec:queue";

    private final StringRedisTemplate redis;

    public RedisRecommendationQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void enqueue(long recommendationId) {
        redis.opsForList().leftPush(KEY, String.valueOf(recommendationId));
    }

    @Override
    public Optional<Long> poll(Duration timeout) {
        String v = redis.opsForList().rightPop(KEY, timeout);
        return v == null ? Optional.empty() : Optional.of(Long.parseLong(v));
    }

    @Override
    public long size() {
        Long size = redis.opsForList().size(KEY);
        return size == null ? 0 : size;
    }
}
