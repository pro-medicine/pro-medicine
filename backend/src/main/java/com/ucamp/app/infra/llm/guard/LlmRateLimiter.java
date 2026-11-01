package com.ucamp.app.infra.llm.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis 기반 분/일 제한 (사용자별, 요청 단위). Redis 장애 시 fail-open.
 * 키: llm:rate:{userId}:{minute|day}:{yyyyMMdd[HHmm]}
 */
@Slf4j
public class LlmRateLimiter {
    private final StringRedisTemplate redis;

    /** INCR + EXPIRE(첫 키만) → 현재 카운트 반환 */
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); " +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])); end; " +
                    "return c;",
            Long.class);

    /** inflight 잠금 해제: 값이 내 UUID일 때만 DEL (만료 후 다른 요청이 잡은 잠금을 지우지 않게) */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class);

    public LlmRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 분당 카운트를 1 올리고 현재 값 반환. Redis 장애 시 empty (fail-open).
     */
    public Optional<Long> incrementMinute(Long userId, String feature) {
        return increment("llm:rate:" + userId + ":" + feature + ":minute:" + minuteKey(), 120);
    }

    /**
     * 일일 카운트를 1 올리고 현재 값 반환. Redis 장애 시 empty (fail-open).
     */
    public Optional<Long> incrementDay(Long userId, String feature) {
        return increment("llm:rate:" + userId + ":" + feature + ":day:" + dayKey(), 90000);
    }

    /**
     * inflight 잠금 시도. 성공하면 lockValue(UUID) 반환, 이미 잠겨 있으면 empty.
     */
    public Optional<String> tryInflight(Long userId, String lockValue, int ttlSeconds) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    "llm:inflight:" + userId, lockValue, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(ok) ? Optional.of(lockValue) : Optional.empty();
        } catch (Exception e) {
            log.warn("Redis inflight lock failed, fail-open: {}", e.getMessage());
            return Optional.of(lockValue); // fail-open
        }
    }

    /**
     * inflight 잠금 해제. 값이 내 lockValue일 때만 삭제.
     */
    public void releaseInflight(Long userId, String lockValue) {
        try {
            redis.execute(RELEASE_SCRIPT, List.of("llm:inflight:" + userId), lockValue);
        } catch (Exception e) {
            log.warn("Redis inflight release failed: {}", e.getMessage());
        }
    }

    /**
     * 킬스위치 확인: Redis 키 llm:kill:{feature} 존재 여부.
     */
    public boolean isKilled(String feature) {
        try {
            return Boolean.TRUE.equals(redis.hasKey("llm:kill:" + feature));
        } catch (Exception e) {
            log.warn("Redis killswitch check failed, fail-open: {}", e.getMessage());
            return false;
        }
    }

    // ---------- 게스트 (IP 기반) ----------

    public Optional<Long> incrementMinuteGuest(String clientIp, String feature) {
        return increment("llm:rate:guest:" + clientIp + ":" + feature + ":minute:" + minuteKey(), 120);
    }

    public Optional<Long> incrementDayGuest(String clientIp, String feature) {
        return increment("llm:rate:guest:" + clientIp + ":" + feature + ":day:" + dayKey(), 90000);
    }

    public Optional<String> tryInflightGuest(String clientIp, String lockValue, int ttlSeconds) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    "llm:inflight:guest:" + clientIp, lockValue, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(ok) ? Optional.of(lockValue) : Optional.empty();
        } catch (Exception e) {
            log.warn("Redis guest inflight lock failed, fail-open: {}", e.getMessage());
            return Optional.of(lockValue);
        }
    }

    public void releaseInflightGuest(String clientIp, String lockValue) {
        try {
            redis.execute(RELEASE_SCRIPT, List.of("llm:inflight:guest:" + clientIp), lockValue);
        } catch (Exception e) {
            log.warn("Redis guest inflight release failed: {}", e.getMessage());
        }
    }

    /**
     * 기능별 일일 호출 카운트를 1 올리고 현재 값 반환.
     */
    public Optional<Long> incrementFeatureBudget(String feature) {
        return increment("llm:budget:" + feature + ":" + dayKey(), 90000);
    }

    /**
     * 기능별 일일 호출 카운트 조회.
     */
    public long getFeatureBudgetCount(String feature) {
        try {
            String val = redis.opsForValue().get("llm:budget:" + feature + ":" + dayKey());
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            log.warn("Redis budget count failed: {}", e.getMessage());
            return 0;
        }
    }

    private Optional<Long> increment(String key, int expireSeconds) {
        try {
            Long count = redis.execute(INCR_SCRIPT, List.of(key), String.valueOf(expireSeconds));
            return Optional.ofNullable(count);
        } catch (Exception e) {
            log.warn("Redis rate limit failed, fail-open: key={}, error={}", key, e.getMessage());
            return Optional.empty(); // fail-open
        }
    }

    private static String minuteKey() {
        var now = java.time.LocalDateTime.now();
        return String.format("%04d%02d%02d%02d%02d", now.getYear(), now.getMonthValue(),
                now.getDayOfMonth(), now.getHour(), now.getMinute());
    }

    private static String dayKey() {
        var now = java.time.LocalDate.now();
        return String.format("%04d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }
}
