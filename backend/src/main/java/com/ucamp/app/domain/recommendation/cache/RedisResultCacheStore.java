package com.ucamp.app.domain.recommendation.cache;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 결과 캐시. 키는 정규화 입력의 SHA-256이라 원문이 Redis에 남지 않는다.
 * Redis 오류는 캐시 미스로 처리한다 (캐시 장애가 추천 실패로 번지지 않게).
 */
@Slf4j
public class RedisResultCacheStore implements ResultCacheStore {
    static final String PREFIX = "rec:llm:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisResultCacheStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CachedLlmOutput> get(String key) {
        try {
            String json = redis.opsForValue().get(PREFIX + key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, CachedLlmOutput.class));
        } catch (RuntimeException e) {
            log.warn("result cache get failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, CachedLlmOutput value, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException e) {
            log.warn("result cache put failed: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void clear() {
        var keys = redis.keys(PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
