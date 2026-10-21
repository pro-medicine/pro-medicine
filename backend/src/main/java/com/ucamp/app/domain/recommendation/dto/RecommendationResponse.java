package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;

import java.time.LocalDateTime;
import java.util.List;

// 저장된 추천 결과. POST(생성)와 GET(조회)이 같은 형식을 쓴다
public record RecommendationResponse(
        Long recommendationId,
        Long responseId,
        String modelName,
        String summary,
        LocalDateTime createdAt,
        List<Item> items
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String ingredient, String reason, int sortOrder) {
        public static Item from(AiRecommendationItem item) {
            return new Item(item.getIngredientName(), item.getReason(), item.getSortOrder());
        }
    }

    public static RecommendationResponse of(AiRecommendation rec, List<AiRecommendationItem> items) {
        return new RecommendationResponse(rec.getRecommendationId(), rec.getResponse().getResponseId(),
                rec.getModelName(), rec.getSummary(), rec.getCreatedAt(), items.stream().map(Item::from).toList());
    }
}
