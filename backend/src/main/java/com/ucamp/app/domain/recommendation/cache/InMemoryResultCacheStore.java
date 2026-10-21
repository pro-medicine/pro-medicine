package com.ucamp.app.domain.recommendation.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 메모리 결과 캐시 (기본값·테스트용). 서버 한 대 안에서만 공유되고 재시작하면 비워진다
public class InMemoryResultCacheStore implements ResultCacheStore {
    private record Entry(CachedLlmOutput value, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedLlmOutput> get(String key) {
        Entry e = entries.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            entries.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    @Override
    public void put(String key, CachedLlmOutput value, Duration ttl) {
        entries.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public void clear() {
        entries.clear();
    }
}
