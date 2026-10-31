package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

/**
 * LLM 가드 총괄 (V5 보안). 요청 진입 시점에서 호출한다.
 * 순서: 차단 목록 → inflight 잠금 → 분/일 제한 → 기능 예산/킬스위치.
 * Redis 장애 시 fail-open (분/일 제한만 건너뜀, 차단 목록은 DB 조회).
 */
@Slf4j
public class LlmGuard {
    private final LlmGuardProperties properties;
    private final LlmBlocklistRepository blocklistRepository;
    private final LlmRateLimiter rateLimiter; // null이면 Redis 없음

    public LlmGuard(LlmGuardProperties properties,
                     LlmBlocklistRepository blocklistRepository,
                     LlmRateLimiter rateLimiter) {
        this.properties = properties;
        this.blocklistRepository = blocklistRepository;
        this.rateLimiter = rateLimiter;
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

        // 4) 분/일 제한 (Redis, fail-open)
        if (rateLimiter != null) {
            try {
                Optional<Long> minuteCount = rateLimiter.incrementMinute(userId);
                if (minuteCount.isPresent() && minuteCount.get() > properties.getLimits().getPerMinute()) {
                    releaseInflight(userId, lockValue);
                    throw new LlmGuardException(ErrorCode.LG_RATE_LIMITED, 60);
                }
                Optional<Long> dayCount = rateLimiter.incrementDay(userId);
                if (dayCount.isPresent() && dayCount.get() > properties.getLimits().getPerDay()) {
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
     * 기능 예산 확인 (LLM 호출 직전, 실제 호출 단위). 예산 초과 시 true 반환.
     * RecommendationService는 이걸로 RuleOnlyPipeline 폴백 결정.
     */
    public boolean isFeatureBudgetExceeded(String feature) {
        int budget = properties.getDailyBudgetFor(feature);
        if (rateLimiter == null) return false;
        try {
            long count = rateLimiter.getFeatureBudgetCount(feature);
            return count >= budget;
        } catch (Exception e) {
            log.warn("Feature budget check failed, fail-open: {}", e.getMessage());
            return false;
        }
    }

    /**
     * inflight 잠금 해제.
     */
    public void releaseInflight(Long userId, String lockValue) {
        if (userId == null || rateLimiter == null) return;
        rateLimiter.releaseInflight(userId, lockValue);
    }
}
