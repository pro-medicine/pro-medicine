package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.RuleOnlyPipeline;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import com.ucamp.app.global.metrics.StageTimer;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmUnavailableException;
import com.ucamp.app.infra.llm.guard.ClientIpResolver;
import com.ucamp.app.infra.llm.guard.LlmGuard;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 추천 생성. 입력 조립 → 파이프라인 실행(LLM, 트랜잭션 없음) → 저장. 요청 스레드가 LLM 응답을 기다린다.
 * 비회원도 자기 responseId로 호출할 수 있다. 소유권 확인은 RecommendationAccess가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationInputAssembler assembler;
    private final PipelineRegistry pipelineRegistry;
    private final RecommendationStore store;
    private final RecommendationProperties properties;
    private final StageTimer stageTimer;
    private final RuleOnlyPipeline ruleOnlyPipeline;
    private final RecommendationAccess recommendationAccess;
    private final LlmGuard llmGuard;
    private final MeterRegistry meterRegistry;

    public static final String FEATURE = "recommendation";
    public static final String FEATURE_GUEST = "recommendation-guest";

    public RecommendationResponse create(Long userId, Long responseId,
                                         String guestToken, HttpServletRequest httpRequest) {
        if (responseId == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_INVALID_REQUEST);
        }
        recommendationAccess.requireResponseOwned(userId, responseId, guestToken);
        String clientIp = userId == null ? ClientIpResolver.resolve(httpRequest) : null;
        String lockValue;
        if (userId == null) {
            lockValue = llmGuard.checkAndLockGuest(clientIp, FEATURE);
        } else {
            lockValue = llmGuard.checkAndLock(userId, FEATURE);
        }
        try {
            String feature = userId == null ? FEATURE_GUEST : FEATURE;
            String version = properties.getPipelineVersion();
            return stageTimer.recordTotal(version, () -> {
                RecommendationResult result = runPipeline(responseId, version, feature);
                return store.save(responseId, result);
            });
        } finally {
            if (userId != null) {
                llmGuard.releaseInflight(userId, lockValue);
            } else {
                llmGuard.releaseGuestInflight(clientIp, lockValue);
            }
        }
    }

    private RecommendationResult runPipeline(Long responseId, String version, String feature) {
        RecommendationInput input = stageTimer.record(StageTimer.ASSEMBLE, version, () -> assembler.assemble(responseId));
        return execute(input, version, feature);
    }

    /**
     * 이미 조립한 입력으로 파이프라인 실행 (V2 정밀 추천). 트랜잭션 없이 부른다.
     * LLM 장애(서킷 OPEN·재시도 소진)면 규칙 기반 폴백, 그 밖의 호출 실패는 R003
     */
    public RecommendationResult runInput(RecommendationInput input, String version) {
        return stageTimer.recordTotal(version, () -> execute(input, version, FEATURE));
    }

    private RecommendationResult execute(RecommendationInput input, String version, String feature) {
        // 기능 예산 초과 → 규칙 기반 폴백 (LLM 호출 없이)
        if (llmGuard.isFeatureBudgetExceeded(feature)) {
            log.warn("recommendation budget exceeded, rule-only fallback: feature={}, responseId={}", feature, input.responseId());
            meterRegistry.counter("recommendation.fallback", "reason", "BUDGET_EXCEEDED").increment();
            return ruleOnlyPipeline.run(input);
        }
        // feature 태그를 입력에 설정 → 파이프라인 → LlmRequest.feature로 전달
        RecommendationInput tagged = input.toBuilder().feature(feature).build();
        try {
            return pipelineRegistry.get(version).run(tagged);
        } catch (LlmUnavailableException e) {
            // 서킷 OPEN·재시도 소진이면 규칙 기반 폴백 (각 파이프라인 코드는 바꾸지 않는다)
            log.warn("LLM unavailable, rule-only fallback: reason={}, responseId={}, sessionId={}", e.getReason(),
                    input.responseId(), input.sessionId());
            meterRegistry.counter("recommendation.fallback", "reason", e.getReason().name()).increment();
            return ruleOnlyPipeline.run(input);
        } catch (LlmCallException e) {
            // 원문 대신 실패 종류만 남긴다
            log.warn("LLM call failed: kind={}, status={}, responseId={}, sessionId={}", e.getKind(), e.getHttpStatus(),
                    input.responseId(), input.sessionId());
            throw new BusinessException(ErrorCode.RECOMMENDATION_LLM_FAILED);
        }
    }

    public RecommendationResponse get(Long recommendationId) {
        return store.get(recommendationId);
    }
}
