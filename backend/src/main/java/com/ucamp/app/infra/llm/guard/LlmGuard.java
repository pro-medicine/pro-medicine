package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * LLM 가드 총괄 (V5 보안). 요청 진입 시점에서 호출한다.
 * 순서: 차단 목록 → inflight 잠금 → 분/일 제한 → 기능 예산/킬스위치.
 * Redis 장애 시 fail-open (분/일 제한만 건너뜀, 차단 목록은 DB 조회).
 * 경고 누적: INJECTION/OUTPUT_INVALID → 24시간 내 3회=30분 정지, 6회=24시간 정지, 이후=영구 차단.
 */
@Slf4j
public class LlmGuard {
    private final LlmGuardProperties properties;
    private final LlmBlocklistRepository blocklistRepository;
    private final LlmUserStrikeRepository strikeRepository;
    private final LlmRateLimiter rateLimiter; // null이면 Redis 없음
    private final FeatureBudgetCounter featureBudgetCounter;

    public LlmGuard(LlmGuardProperties properties,
                     LlmBlocklistRepository blocklistRepository,
                     LlmUserStrikeRepository strikeRepository,
                     LlmRateLimiter rateLimiter,
                     FeatureBudgetCounter featureBudgetCounter) {
        this.properties = properties;
        this.blocklistRepository = blocklistRepository;
        this.strikeRepository = strikeRepository;
        this.rateLimiter = rateLimiter;
        this.featureBudgetCounter = featureBudgetCounter;
    }

    /**
     * 요청 진입 가드. 통과하면 inflight 잠금 값(UUID)을 반환한다.
     * 호출 측은 finally에서 {@link #releaseInflight}를 호출해야 한다.
     *
     * @param userId  요청자 (null이면 비회원 — 가드 건너뜀)
     * @param feature 기능 이름 (recommendation, personalized, chat)
     * @return inflight 잠금 UUID (Redis 없으면 dummy UUID)
     * @throws LlmGuardException 차단/제한/예산 초과 시
     */
    public String checkAndLock(Long userId, String feature) {
        // 비회원은 가드 건너뜀 (V1 설문 비회원 추천 허용)
        if (userId == null) {
            return UUID.randomUUID().toString();
        }

        // 1) 기능 ON/OFF + 킬스위치
        if (!properties.isFeatureEnabled(feature) || (rateLimiter != null && rateLimiter.isKilled(feature))) {
            throw new LlmGuardException(ErrorCode.LG_UNAVAILABLE);
        }

        // 2) 차단 목록 (DB 조회 — Redis 장애와 무관)
        Optional<LlmBlocklist> block = blocklistRepository.findActiveBlock(userId);
        if (block.isPresent()) {
            LlmBlocklist b = block.get();
            if (b.isPermanent()) {
                throw new LlmGuardException(ErrorCode.LG_BLOCKED);
            }
            throw new LlmGuardException(ErrorCode.LG_SUSPENDED, b.remainingSeconds());
        }

        // 3) inflight 잠금
        String lockValue = UUID.randomUUID().toString();
        if (rateLimiter != null) {
            Optional<String> locked = rateLimiter.tryInflight(userId, lockValue, properties.getInflightTtlSeconds());
            if (locked.isEmpty()) {
                throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 5);
            }
        }

        // 4) 분/일 제한 (Redis, fail-open, 기능별)
        if (rateLimiter != null) {
            LlmGuardProperties.Limits featureLimits = properties.getLimitsFor(feature);
            try {
                Optional<Long> minuteCount = rateLimiter.incrementMinute(userId, feature);
                if (minuteCount.isPresent() && minuteCount.get() > featureLimits.getPerMinute()) {
                    releaseInflight(userId, lockValue);
                    throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 60);
                }
                Optional<Long> dayCount = rateLimiter.incrementDay(userId, feature);
                if (dayCount.isPresent() && dayCount.get() > featureLimits.getPerDay()) {
                    releaseInflight(userId, lockValue);
                    throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 3600);
                }
            } catch (LlmGuardException e) {
                throw e; // 위에서 던진 거면 그대로
            } catch (Exception e) {
                log.warn("Rate limit check failed, fail-open: {}", e.getMessage());
            }
        }

        return lockValue;
    }

    /**
     * 게스트 요청 진입 가드. IP 기반 inflight 잠금 + 분/일 제한.
     * strike/blocklist는 게스트에 적용하지 않는다 (IP 변경이 쉬우므로).
     *
     * @param clientIp 클라이언트 IP (null이면 429)
     * @param feature  기능 이름
     * @return inflight 잠금 UUID
     */
    public String checkAndLockGuest(String clientIp, String feature) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 60);
        }

        // 킬스위치
        if (!properties.isFeatureEnabled(feature) || (rateLimiter != null && rateLimiter.isKilled(feature))) {
            throw new LlmGuardException(ErrorCode.LG_UNAVAILABLE);
        }

        String lockValue = UUID.randomUUID().toString();
        if (rateLimiter != null) {
            // inflight 잠금
            Optional<String> locked = rateLimiter.tryInflightGuest(clientIp, lockValue, properties.getInflightTtlSeconds());
            if (locked.isEmpty()) {
                throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 5);
            }

            // 분/일 제한 (게스트 전용 설정)
            LlmGuardProperties.Limits guestLimits = properties.getLimitsFor("guest");
            try {
                Optional<Long> minuteCount = rateLimiter.incrementMinuteGuest(clientIp, feature);
                if (minuteCount.isPresent() && minuteCount.get() > guestLimits.getPerMinute()) {
                    releaseGuestInflight(clientIp, lockValue);
                    throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 60);
                }
                Optional<Long> dayCount = rateLimiter.incrementDayGuest(clientIp, feature);
                if (dayCount.isPresent() && dayCount.get() > guestLimits.getPerDay()) {
                    releaseGuestInflight(clientIp, lockValue);
                    throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 3600);
                }
            } catch (LlmGuardException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Guest rate limit check failed, fail-open: {}", e.getMessage());
            }
        }
        return lockValue;
    }

    /** 게스트 inflight 잠금 해제 */
    public void releaseGuestInflight(String clientIp, String lockValue) {
        if (clientIp == null || rateLimiter == null) return;
        rateLimiter.releaseInflightGuest(clientIp, lockValue);
    }

    /**
     * 기능 예산 확인 (LLM 호출 직전, 실제 호출 단위). 예산 초과 시 true 반환.
     * RecommendationService는 이걸로 RuleOnlyPipeline 폴백 결정.
     */
    public boolean isFeatureBudgetExceeded(String feature) {
        int budget = properties.getDailyBudgetFor(feature);
        long memoryCount = featureBudgetCounter.get(feature);
        long redisCount = 0;
        if (rateLimiter != null) {
            try {
                redisCount = rateLimiter.getFeatureBudgetCount(feature);
            } catch (Exception e) {
                log.warn("Feature budget check Redis failed, using memory only: {}", e.getMessage());
            }
        }
        return Math.max(memoryCount, redisCount) >= budget;
    }

    /**
     * inflight 잠금 해제.
     */
    public void releaseInflight(Long userId, String lockValue) {
        if (userId == null || rateLimiter == null) return;
        rateLimiter.releaseInflight(userId, lockValue);
    }

    /**
     * 경고 기록 + 누적에 따라 정지/차단 (INJECTION, OUTPUT_INVALID만).
     * 24시간 내 경고 횟수: suspendThreshold → 30분 정지, longSuspendThreshold → 24시간 정지, 그 이후 → 영구 차단.
     */
    public void recordStrike(Long userId, String reason) {
        if (userId == null) return;
        try {
            strikeRepository.save(new LlmUserStrike(userId, reason));
            long recent = strikeRepository.countRecent(userId, LocalDateTime.now().minusHours(24));
            LlmGuardProperties.Strikes s = properties.getStrikes();
            if (recent > s.getLongSuspendThreshold()) {
                blocklistRepository.save(LlmBlocklist.permanentBlock(userId, "STRIKE_PERMANENT"));
                log.warn("llm guard: permanent block userId={}, strikes={}", userId, recent);
            } else if (recent >= s.getLongSuspendThreshold()) {
                blocklistRepository.save(LlmBlocklist.suspend(userId, "STRIKE_LONG_SUSPEND", s.getLongSuspendSeconds()));
                log.warn("llm guard: 24h suspend userId={}, strikes={}", userId, recent);
            } else if (recent >= s.getSuspendThreshold()) {
                blocklistRepository.save(LlmBlocklist.suspend(userId, "STRIKE_SUSPEND", s.getSuspendSeconds()));
                log.info("llm guard: 30min suspend userId={}, strikes={}", userId, recent);
            }
        } catch (Exception e) {
            log.error("llm guard: failed to record strike userId={}, reason={}", userId, reason, e);
        }
    }
}
