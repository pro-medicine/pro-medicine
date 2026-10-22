package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;

import tools.jackson.core.JacksonException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 저장된 추천 결과. POST(생성)와 GET(조회)이 같은 형식을 쓴다.
 * items = 최종 추천, excludedItems = 규칙으로 제외된 성분과 이유 (STEP 07)
 * status = PENDING | PROCESSING | DONE | FAILED (STEP 09 비동기). 동기 API 결과는 DONE
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
    /** STEP 10: LLM 장애로 규칙 기반 폴백한 결과의 model_name */
    public static final String RULE_ONLY = "RULE_ONLY";
    public static final String FALLBACK_NOTICE = "AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String ingredient, String reason, int sortOrder, BigDecimal amount, String unit,
                       BigDecimal currentIntake, String intakeStatus, String doseNote, List<String> warnings,
                       List<String> appliedRuleIds) {
        static Item from(AiRecommendationItem item, ObjectMapper objectMapper) {
            return new Item(item.getIngredientName(), item.getReason(), item.getSortOrder(),
                    item.getRecommendedAmount(), item.getUnit(), item.getCurrentIntake(), item.getIntakeStatus(),
                    item.getDoseNote(), warnings(item.getWarnings(), objectMapper),
                    item.getAppliedRuleIds() == null ? null : Arrays.asList(item.getAppliedRuleIds().split(",")));
        }

        private static List<String> warnings(String json, ObjectMapper objectMapper) {
            if (json == null) {
                return null;
            }
            try {
                return objectMapper.readValue(json, new TypeReference<>() {
                });
            } catch (JacksonException e) {
                return List.of(json);
            }
        }
    }

    public static RecommendationResponse of(AiRecommendation rec, List<AiRecommendationItem> items,
                                            ObjectMapper objectMapper) {
        return new RecommendationResponse(rec.getRecommendationId(), rec.getResponse().getResponseId(),
                rec.getStatus(), rec.getErrorCode(), rec.getModelName(), rec.getSummary(), rec.getCreatedAt(),
                items.stream().filter(i -> !i.isExcluded()).map(i -> Item.from(i, objectMapper)).toList(),
                items.stream().filter(AiRecommendationItem::isExcluded).map(i -> Item.from(i, objectMapper)).toList(),
                RULE_ONLY.equals(rec.getModelName()),
                RULE_ONLY.equals(rec.getModelName()) ? FALLBACK_NOTICE : null);
    }
}
