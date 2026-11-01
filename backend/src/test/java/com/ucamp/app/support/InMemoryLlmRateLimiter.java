package com.ucamp.app.support;

import com.ucamp.app.infra.llm.guard.LlmRateLimiter;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 없이 동작하는 인메모리 LlmRateLimiter. 통합 테스트에서 rate limit을 검증할 때 사용한다.
 */
public class InMemoryLlmRateLimiter extends LlmRateLimiter {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> inflights = new ConcurrentHashMap<>();

    public InMemoryLlmRateLimiter() {
        super(null);
    }

    public void reset() {
        counters.clear();
        inflights.clear();
    }

    // ---------- member ----------

    @Override
    public Optional<Long> incrementMinute(Long userId, String feature) {
        return Optional.of(inc("minute:" + userId + ":" + feature));
    }

    @Override
    public Optional<Long> incrementDay(Long userId, String feature) {
        return Optional.of(inc("day:" + userId + ":" + feature));
    }

    @Override
    public Optional<String> tryInflight(Long userId, String lockValue, int ttlSeconds) {
        String key = "inflight:" + userId;
        String prev = inflights.putIfAbsent(key, lockValue);
        return prev == null ? Optional.of(lockValue) : Optional.empty();
    }

    @Override
    public void releaseInflight(Long userId, String lockValue) {
        inflights.remove("inflight:" + userId, lockValue);
    }

    // ---------- guest ----------

    @Override
    public Optional<Long> incrementMinuteGuest(String clientIp, String feature) {
        return Optional.of(inc("guest:minute:" + clientIp + ":" + feature));
    }

    @Override
    public Optional<Long> incrementDayGuest(String clientIp, String feature) {
        return Optional.of(inc("guest:day:" + clientIp + ":" + feature));
    }

    @Override
    public Optional<String> tryInflightGuest(String clientIp, String lockValue, int ttlSeconds) {
        String key = "guest:inflight:" + clientIp;
        String prev = inflights.putIfAbsent(key, lockValue);
        return prev == null ? Optional.of(lockValue) : Optional.empty();
    }

    @Override
    public void releaseInflightGuest(String clientIp, String lockValue) {
        inflights.remove("guest:inflight:" + clientIp, lockValue);
    }

    // ---------- common ----------

    @Override
    public boolean isKilled(String feature) {
        return false;
    }

    @Override
    public Optional<Long> incrementFeatureBudget(String feature) {
        return Optional.of(inc("budget:" + feature));
    }

    @Override
    public long getFeatureBudgetCount(String feature) {
        AtomicLong c = counters.get("budget:" + feature);
        return c != null ? c.get() : 0;
    }

    private long inc(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }
}
