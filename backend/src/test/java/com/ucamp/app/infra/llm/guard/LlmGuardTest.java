package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5 LlmGuard 단위 테스트. Redis·DB 없이 Fake/Mock으로 동작 검증.
 */
class LlmGuardTest {
    LlmGuardProperties properties;
    FakeBlocklistRepository blocklistRepo;
    FakeStrikeRepository strikeRepo;
    FakeRateLimiter rateLimiter;
    FeatureBudgetCounter budgetCounter;
    LlmGuard guard;

    @BeforeEach
    void setUp() {
        properties = new LlmGuardProperties();
        properties.setEnabled(Map.of("recommendation", true, "personalized", true, "chat", true));
        properties.setLimits(limits(10, 100));
        properties.setFeatureLimits(Map.of(
                "recommendation", limits(3, 20),
                "personalized", limits(10, 30),
                "chat", limits(6, 50),
                "guest", limits(2, 3)
        ));
        properties.setDailyBudget(Map.of("recommendation", 100, "personalized", 50, "chat", 100,
                "recommendation-guest", 300));
        properties.setInflightTtlSeconds(120);

        blocklistRepo = new FakeBlocklistRepository();
        strikeRepo = new FakeStrikeRepository();
        rateLimiter = new FakeRateLimiter();
        budgetCounter = new FeatureBudgetCounter(null); // no DB init
        guard = new LlmGuard(properties, blocklistRepo, strikeRepo, rateLimiter, budgetCounter);
    }

    // ---------- 비회원 ----------

    @Test
    @DisplayName("비회원(userId=null) → 가드 건너뜀, UUID 반환")
    void null_user_passes() {
        String lock = guard.checkAndLock(null, "chat");
        assertThat(lock).isNotBlank();
    }

    // ---------- 킬스위치 ----------

    @Test
    @DisplayName("킬스위치 off → 503")
    void killswitch_blocks() {
        rateLimiter.killed = true;
        assertThatThrownBy(() -> guard.checkAndLock(1L, "chat"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_UNAVAILABLE));
    }

    @Test
    @DisplayName("기능 disabled → 503")
    void feature_disabled_blocks() {
        properties.setEnabled(Map.of("chat", false));
        assertThatThrownBy(() -> guard.checkAndLock(1L, "chat"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_UNAVAILABLE));
    }

    // ---------- 차단 목록 ----------

    @Test
    @DisplayName("영구 차단 → 403")
    void permanent_block() {
        blocklistRepo.activeBlock = Optional.of(LlmBlocklist.permanentBlock(1L, "TEST"));
        assertThatThrownBy(() -> guard.checkAndLock(1L, "chat"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_BLOCKED));
    }

    @Test
    @DisplayName("일시 정지 → 429 + retryAfter")
    void temporary_suspend() {
        blocklistRepo.activeBlock = Optional.of(LlmBlocklist.suspend(1L, "TEST", 1800));
        assertThatThrownBy(() -> guard.checkAndLock(1L, "chat"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> {
                    LlmGuardException ex = (LlmGuardException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LG_SUSPENDED);
                    assertThat(ex.getRetryAfterSeconds()).isGreaterThan(0);
                });
    }

    // ---------- inflight 잠금 ----------

    @Test
    @DisplayName("동시 2요청 → 두 번째 429")
    void inflight_lock() {
        rateLimiter.inflightLocked = true;
        assertThatThrownBy(() -> guard.checkAndLock(1L, "chat"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    // ---------- 분/일 제한 (기능별) ----------

    @Test
    @DisplayName("기능별 분당 제한 초과 → 429")
    void per_feature_minute_limit() {
        // recommendation은 분당 3
        rateLimiter.minuteCount = 4L;
        assertThatThrownBy(() -> guard.checkAndLock(1L, "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("기능별 일일 제한 초과 → 429")
    void per_feature_day_limit() {
        rateLimiter.dayCount = 21L;
        assertThatThrownBy(() -> guard.checkAndLock(1L, "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("분당 제한 이내 → 통과")
    void within_limit_passes() {
        rateLimiter.minuteCount = 2L;
        rateLimiter.dayCount = 5L;
        String lock = guard.checkAndLock(1L, "recommendation");
        assertThat(lock).isNotBlank();
    }

    // ---------- 기능 예산 (메모리 + Redis) ----------

    @Test
    @DisplayName("메모리 예산 초과 → Redis 없어도 true")
    void memory_budget_exceeded() {
        LlmGuard noRedisGuard = new LlmGuard(properties, blocklistRepo, strikeRepo, null, budgetCounter);
        for (int i = 0; i < 100; i++) budgetCounter.increment("recommendation");
        assertThat(noRedisGuard.isFeatureBudgetExceeded("recommendation")).isTrue();
    }

    @Test
    @DisplayName("Redis 예산 초과, 메모리 이하 → max로 true")
    void redis_budget_exceeded() {
        rateLimiter.budgetCount = 100L;
        assertThat(guard.isFeatureBudgetExceeded("recommendation")).isTrue();
    }

    @Test
    @DisplayName("Redis 다운 → 메모리만으로 예산 확인")
    void redis_down_memory_fallback() {
        LlmGuard noRedisGuard = new LlmGuard(properties, blocklistRepo, strikeRepo, null, budgetCounter);
        assertThat(noRedisGuard.isFeatureBudgetExceeded("recommendation")).isFalse();
        for (int i = 0; i < 100; i++) budgetCounter.increment("recommendation");
        assertThat(noRedisGuard.isFeatureBudgetExceeded("recommendation")).isTrue();
    }

    @Test
    @DisplayName("예산 이내 → false")
    void budget_within_limit() {
        rateLimiter.budgetCount = 10L;
        budgetCounter.increment("recommendation");
        assertThat(guard.isFeatureBudgetExceeded("recommendation")).isFalse();
    }

    // ---------- 경고 누적 ----------

    @Test
    @DisplayName("경고 3회 → 30분 정지")
    void strike_suspend() {
        strikeRepo.recentCount = 3;
        guard.recordStrike(1L, "INJECTION");
        assertThat(blocklistRepo.saved).isNotNull();
        assertThat(blocklistRepo.saved.getReason()).isEqualTo("STRIKE_SUSPEND");
    }

    @Test
    @DisplayName("경고 6회 → 24시간 정지")
    void strike_long_suspend() {
        strikeRepo.recentCount = 6;
        guard.recordStrike(1L, "INJECTION");
        assertThat(blocklistRepo.saved).isNotNull();
        assertThat(blocklistRepo.saved.getReason()).isEqualTo("STRIKE_LONG_SUSPEND");
    }

    @Test
    @DisplayName("경고 7회 → 영구 차단")
    void strike_permanent_block() {
        strikeRepo.recentCount = 7;
        guard.recordStrike(1L, "INJECTION");
        assertThat(blocklistRepo.saved).isNotNull();
        assertThat(blocklistRepo.saved.getReason()).isEqualTo("STRIKE_PERMANENT");
        assertThat(blocklistRepo.saved.isPermanent()).isTrue();
    }

    // ---------- 게스트 가드 ----------

    @Test
    @DisplayName("게스트 정상 통과 → UUID 반환")
    void guest_normal_passes() {
        String lock = guard.checkAndLockGuest("192.168.1.1", "recommendation");
        assertThat(lock).isNotBlank();
    }

    @Test
    @DisplayName("게스트 IP null → 429")
    void guest_null_ip_blocked() {
        assertThatThrownBy(() -> guard.checkAndLockGuest(null, "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("게스트 IP 빈 문자열 → 429")
    void guest_blank_ip_blocked() {
        assertThatThrownBy(() -> guard.checkAndLockGuest("  ", "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("게스트 동시 요청(inflight 잠금) → 두 번째 429")
    void guest_inflight_lock() {
        rateLimiter.guestInflightLocked = true;
        assertThatThrownBy(() -> guard.checkAndLockGuest("10.0.0.1", "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("게스트 분당 제한 초과 → 429")
    void guest_minute_limit() {
        rateLimiter.guestMinuteCount = 3L; // guest limit: 2/min
        assertThatThrownBy(() -> guard.checkAndLockGuest("10.0.0.1", "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("게스트 일일 제한 초과 → 429")
    void guest_day_limit() {
        rateLimiter.guestDayCount = 4L; // guest limit: 3/day
        assertThatThrownBy(() -> guard.checkAndLockGuest("10.0.0.1", "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_RATE_LIMITED));
    }

    @Test
    @DisplayName("게스트 킬스위치 → 503")
    void guest_killswitch() {
        rateLimiter.killed = true;
        assertThatThrownBy(() -> guard.checkAndLockGuest("10.0.0.1", "recommendation"))
                .isInstanceOf(LlmGuardException.class)
                .satisfies(e -> assertThat(((LlmGuardException) e).getErrorCode()).isEqualTo(ErrorCode.LG_UNAVAILABLE));
    }

    @Test
    @DisplayName("게스트 inflight 잠금 해제는 releaseGuestInflight로")
    void guest_inflight_release() {
        String lock = guard.checkAndLockGuest("10.0.0.1", "recommendation");
        guard.releaseGuestInflight("10.0.0.1", lock);
        assertThat(rateLimiter.guestInflightReleased).isTrue();
    }

    @Test
    @DisplayName("게스트 제한 이내 → 통과")
    void guest_within_limit_passes() {
        rateLimiter.guestMinuteCount = 1L;
        rateLimiter.guestDayCount = 2L;
        String lock = guard.checkAndLockGuest("10.0.0.1", "recommendation");
        assertThat(lock).isNotBlank();
    }

    // ---------- helpers ----------

    static LlmGuardProperties.Limits limits(int perMinute, int perDay) {
        LlmGuardProperties.Limits l = new LlmGuardProperties.Limits();
        l.setPerMinute(perMinute);
        l.setPerDay(perDay);
        return l;
    }

    // ---------- Fake implementations ----------

    static class FakeBlocklistRepository implements LlmBlocklistRepository {
        Optional<LlmBlocklist> activeBlock = Optional.empty();
        LlmBlocklist saved;

        @Override public Optional<LlmBlocklist> findActiveBlock(Long userId) { return activeBlock; }
        @Override public <S extends LlmBlocklist> S save(S entity) { saved = entity; return entity; }

        // unused JpaRepository methods
        @Override public java.util.List<LlmBlocklist> findAll() { return java.util.List.of(); }
        @Override public java.util.List<LlmBlocklist> findAllById(Iterable<Long> ids) { return java.util.List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(LlmBlocklist entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends LlmBlocklist> entities) {}
        @Override public void deleteAll() {}
        @Override public <S extends LlmBlocklist> java.util.List<S> saveAll(Iterable<S> entities) { return java.util.List.of(); }
        @Override public Optional<LlmBlocklist> findById(Long id) { return Optional.empty(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public void flush() {}
        @Override public <S extends LlmBlocklist> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends LlmBlocklist> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { return java.util.List.of(); }
        @Override public void deleteAllInBatch(Iterable<LlmBlocklist> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public LlmBlocklist getOne(Long id) { return null; }
        @Override public LlmBlocklist getById(Long id) { return null; }
        @Override public LlmBlocklist getReferenceById(Long id) { return null; }
        @Override public <S extends LlmBlocklist> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends LlmBlocklist> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example) { return java.util.List.of(); }
        @Override public <S extends LlmBlocklist> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return java.util.List.of(); }
        @Override public <S extends LlmBlocklist> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends LlmBlocklist> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends LlmBlocklist> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends LlmBlocklist, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public java.util.List<LlmBlocklist> findAll(org.springframework.data.domain.Sort sort) { return java.util.List.of(); }
        @Override public org.springframework.data.domain.Page<LlmBlocklist> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class FakeStrikeRepository implements LlmUserStrikeRepository {
        long recentCount = 0;

        @Override public long countRecent(Long userId, java.time.LocalDateTime since) { return recentCount; }
        @Override public <S extends LlmUserStrike> S save(S entity) { return entity; }

        // unused
        @Override public java.util.List<LlmUserStrike> findAll() { return java.util.List.of(); }
        @Override public java.util.List<LlmUserStrike> findAllById(Iterable<Long> ids) { return java.util.List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(LlmUserStrike entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends LlmUserStrike> entities) {}
        @Override public void deleteAll() {}
        @Override public <S extends LlmUserStrike> java.util.List<S> saveAll(Iterable<S> entities) { return java.util.List.of(); }
        @Override public Optional<LlmUserStrike> findById(Long id) { return Optional.empty(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public void flush() {}
        @Override public <S extends LlmUserStrike> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends LlmUserStrike> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { return java.util.List.of(); }
        @Override public void deleteAllInBatch(Iterable<LlmUserStrike> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public LlmUserStrike getOne(Long id) { return null; }
        @Override public LlmUserStrike getById(Long id) { return null; }
        @Override public LlmUserStrike getReferenceById(Long id) { return null; }
        @Override public <S extends LlmUserStrike> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends LlmUserStrike> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example) { return java.util.List.of(); }
        @Override public <S extends LlmUserStrike> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return java.util.List.of(); }
        @Override public <S extends LlmUserStrike> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends LlmUserStrike> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends LlmUserStrike> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends LlmUserStrike, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public java.util.List<LlmUserStrike> findAll(org.springframework.data.domain.Sort sort) { return java.util.List.of(); }
        @Override public org.springframework.data.domain.Page<LlmUserStrike> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class FakeRateLimiter extends LlmRateLimiter {
        boolean killed = false;
        boolean inflightLocked = false;
        boolean guestInflightLocked = false;
        Long minuteCount = 0L;
        Long dayCount = 0L;
        Long guestMinuteCount = 0L;
        Long guestDayCount = 0L;
        long budgetCount = 0L;
        boolean guestInflightReleased = false;

        FakeRateLimiter() { super(null); }

        @Override public boolean isKilled(String feature) { return killed; }
        @Override public Optional<String> tryInflight(Long userId, String lockValue, int ttlSeconds) {
            return inflightLocked ? Optional.empty() : Optional.of(lockValue);
        }
        @Override public void releaseInflight(Long userId, String lockValue) {}
        @Override public Optional<Long> incrementMinute(Long userId, String feature) { return Optional.of(minuteCount); }
        @Override public Optional<Long> incrementDay(Long userId, String feature) { return Optional.of(dayCount); }
        @Override public Optional<Long> incrementFeatureBudget(String feature) { return Optional.of(budgetCount); }
        @Override public long getFeatureBudgetCount(String feature) { return budgetCount; }

        // guest methods
        @Override public Optional<String> tryInflightGuest(String clientIp, String lockValue, int ttlSeconds) {
            return guestInflightLocked ? Optional.empty() : Optional.of(lockValue);
        }
        @Override public void releaseInflightGuest(String clientIp, String lockValue) {
            guestInflightReleased = true;
        }
        @Override public Optional<Long> incrementMinuteGuest(String clientIp, String feature) { return Optional.of(guestMinuteCount); }
        @Override public Optional<Long> incrementDayGuest(String clientIp, String feature) { return Optional.of(guestDayCount); }
    }
}
