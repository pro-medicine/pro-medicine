package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.RuleOnlyPipeline;
import com.ucamp.app.domain.recommendation.queue.RecommendationQueue;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import com.ucamp.app.global.metrics.StageTimer;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 추천 생성. recommendation.api-mode로 처리 구조를 고른다 (비교 측정용, 둘 다 유지).
 * - sync: 입력 조립 → 파이프라인 실행(LLM, 트랜잭션 없음) → 저장. 요청 스레드가 LLM 응답을 기다린다
 * - queue: PENDING 행 생성 → 큐 → 즉시 응답(202). 워커가 process()로 처리, 클라이언트는 GET으로 상태를 본다
 * 비회원도 자기 responseId로 호출할 수 있다 (가로채기 방지는 범위 밖, progress 미룬 것 참고).
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
    private final RecommendationQueue queue;
    private final RuleOnlyPipeline ruleOnlyPipeline;
    private final MeterRegistry meterRegistry;
    // 같은 responseId의 동시 접수를 한 번만 만들기 위한 잠금 (서버 한 대 기준. 여러 대면 DB 유니크나 Redis SETNX 필요)
    private final ConcurrentHashMap<Long, Object> submitLocks = new ConcurrentHashMap<>();

    public RecommendationResponse create(Long responseId) {
        if (responseId == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_INVALID_REQUEST);
        }
        return properties.isQueueMode() ? submit(responseId) : createSync(responseId);
    }

    private RecommendationResponse createSync(Long responseId) {
        String version = properties.getPipelineVersion();
        return stageTimer.recordTotal(version, () -> {
            RecommendationResult result = runPipeline(responseId, version);
            return store.save(responseId, result);
        });
    }

    /** 비동기 접수. 같은 responseId로 진행 중이거나 끝난 작업이 있으면 그것을 돌려준다 (멱등) */
    private RecommendationResponse submit(Long responseId) {
        Object lock = submitLocks.computeIfAbsent(responseId, k -> new Object());
        try {
            synchronized (lock) {
                return store.findActiveOrDone(responseId).orElseGet(() -> {
                    RecommendationResponse pending = store.createPending(responseId);
                    queue.enqueue(pending.recommendationId());
                    return pending;
                });
            }
        } finally {
            submitLocks.remove(responseId, lock);
        }
    }

    /** 워커가 호출. 이미 다른 워커가 가져간 작업은 건너뛴다 */
    public void process(Long recommendationId) {
        Long responseId = store.markProcessing(recommendationId).orElse(null);
        if (responseId == null) {
            return;
        }
        String version = properties.getPipelineVersion();
        try {
            RecommendationResult result = stageTimer.recordTotal(version, () -> runPipeline(responseId, version));
            store.complete(recommendationId, result);
        } catch (BusinessException e) {
            store.fail(recommendationId, e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            log.warn("recommendation worker failed: id={}, error={}", recommendationId, e.getClass().getSimpleName());
            store.fail(recommendationId, "INTERNAL");
        }
    }

    private RecommendationResult runPipeline(Long responseId, String version) {
        RecommendationInput input = stageTimer.record(StageTimer.ASSEMBLE, version, () -> assembler.assemble(responseId));
        return execute(input, version);
    }

    /**
     * 이미 조립한 입력으로 파이프라인 실행 (V2 정밀 추천). 트랜잭션 없이 부른다.
     * LLM 장애(서킷 OPEN·재시도 소진)면 규칙 기반 폴백, 그 밖의 호출 실패는 R003
     */
    public RecommendationResult runInput(RecommendationInput input, String version) {
        return stageTimer.recordTotal(version, () -> execute(input, version));
    }

    private RecommendationResult execute(RecommendationInput input, String version) {
        try {
            return pipelineRegistry.get(version).run(input);
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
