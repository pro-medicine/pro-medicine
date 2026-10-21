package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import com.ucamp.app.infra.llm.LlmCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 동기 추천 생성: 입력 조립 → 파이프라인 실행(LLM, 트랜잭션 없음) → 저장.
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

    public RecommendationResponse create(Long responseId) {
        if (responseId == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_INVALID_REQUEST);
        }
        RecommendationInput input = assembler.assemble(responseId);
        RecommendationResult result;
        try {
            result = pipelineRegistry.get(properties.getPipelineVersion()).run(input);
        } catch (LlmCallException e) {
            // 원문 대신 실패 종류만 남긴다
            log.warn("LLM call failed: kind={}, status={}, responseId={}", e.getKind(), e.getHttpStatus(), responseId);
            throw new BusinessException(ErrorCode.RECOMMENDATION_LLM_FAILED);
        }
        return store.save(responseId, result);
    }

    public RecommendationResponse get(Long recommendationId) {
        return store.get(recommendationId);
    }
}
