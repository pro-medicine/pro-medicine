package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 저장된 추천 결과. POST(생성)와 GET(조회)이 같은 형식을 쓴다. 엔티티 → 응답 변환은 RecommendationMapper.
 * items = 최종 추천, excludedItems = 규칙으로 제외된 성분과 이유
 * status = PENDING | PROCESSING | DONE | FAILED (queue 모드). sync 모드 결과는 DONE
 * fallback·notice = LLM 장애로 규칙 기반 기본 안내를 보낸 경우
 */
public record RecommendationResponse(
        Long recommendationId,
        Long responseId,
        String status,
        String errorCode,
        String modelName,
        String summary,
        LocalDateTime createdAt,
        List<Item> items,
        List<Item> excludedItems,
        boolean fallback,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notice
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String ingredient, String reason, int sortOrder, BigDecimal amount, String unit,
                       BigDecimal currentIntake, String intakeStatus, String doseNote, List<String> warnings,
                       List<String> appliedRuleIds) {
    }

    /** 처리가 끝났는지 (sync 모드 200, queue 접수 202를 고를 때 쓴다) */
    @JsonIgnore
    public boolean isDone() {
        return AiRecommendation.DONE.equals(status);
    }
}
