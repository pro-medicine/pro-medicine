package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 저장된 추천 결과. POST(생성)와 GET(조회)이 같은 형식을 쓴다.
 * items = 최종 추천, excludedItems = 규칙으로 제외된 성분과 이유 (STEP 07)
 */
public record RecommendationResponse(
        Long recommendationId,
        Long responseId,
        String modelName,
        String summary,
        LocalDateTime createdAt,
        List<Item> items,
        List<Item> excludedItems
) {
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
            } catch (IOException e) {
                return List.of(json);
            }
        }
    }

    public static RecommendationResponse of(AiRecommendation rec, List<AiRecommendationItem> items,
                                            ObjectMapper objectMapper) {
        return new RecommendationResponse(rec.getRecommendationId(), rec.getResponse().getResponseId(),
                rec.getModelName(), rec.getSummary(), rec.getCreatedAt(),
                items.stream().filter(i -> !i.isExcluded()).map(i -> Item.from(i, objectMapper)).toList(),
                items.stream().filter(AiRecommendationItem::isExcluded).map(i -> Item.from(i, objectMapper)).toList());
    }
}
