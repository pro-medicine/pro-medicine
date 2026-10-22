package com.ucamp.app.domain.recommendation.cache;

import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 결과 캐시 조회·저장 + Counter recommendation.cache{result=hit|miss}. TTL = recommendation.cache.ttl
@Component
@RequiredArgsConstructor
public class ResultCacheService {
    public static final String METRIC = "recommendation.cache";

    private final ResultCacheStore store;
    private final MeterRegistry meterRegistry;
    private final RecommendationProperties properties;

    public Optional<CachedLlmOutput> get(String key) {
        Optional<CachedLlmOutput> hit = store.get(key);
        meterRegistry.counter(METRIC, "result", hit.isPresent() ? "hit" : "miss").increment();
        return hit;
    }

    public void put(String key, CachedLlmOutput value) {
        store.put(key, value, properties.getCache().getTtl());
    }

    public void clear() {
        store.clear();
    }
}
