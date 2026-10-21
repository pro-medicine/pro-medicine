package com.ucamp.app.domain.recommendation.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

// 결과 캐시 조회·저장 + Counter recommendation.cache{result=hit|miss}
@Component
public class ResultCacheService {
    public static final String METRIC = "recommendation.cache";

    private final ResultCacheStore store;
    private final MeterRegistry meterRegistry;
    private final Duration ttl;

    public ResultCacheService(ResultCacheStore store, MeterRegistry meterRegistry,
                              @Value("${recommendation.cache.ttl:7d}") Duration ttl) {
        this.store = store;
        this.meterRegistry = meterRegistry;
        this.ttl = ttl;
    }

    public Optional<CachedLlmOutput> get(String key) {
        Optional<CachedLlmOutput> hit = store.get(key);
        meterRegistry.counter(METRIC, "result", hit.isPresent() ? "hit" : "miss").increment();
        return hit;
    }

    public void put(String key, CachedLlmOutput value) {
        store.put(key, value, ttl);
    }

    public void clear() {
        store.clear();
    }
}
