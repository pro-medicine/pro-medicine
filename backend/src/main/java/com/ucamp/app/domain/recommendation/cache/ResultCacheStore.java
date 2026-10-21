package com.ucamp.app.domain.recommendation.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 결과 캐시 저장소. 구현: Redis(recommendation.cache.store=redis) / 메모리(기본, 테스트).
 * Redis 서버나 Docker 없이도 테스트가 돌도록 인터페이스 뒤에 둔다.
 */
public interface ResultCacheStore {
    Optional<CachedLlmOutput> get(String key);

    void put(String key, CachedLlmOutput value, Duration ttl);

    void clear();
}
