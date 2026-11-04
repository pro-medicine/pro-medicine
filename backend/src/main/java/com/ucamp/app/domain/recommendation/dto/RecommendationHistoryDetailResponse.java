package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.chat.dto.ChatCard;

import java.util.List;

/**
 * 추천 기록 하나의 성분 카드 + 제품 (V7.1). 카드 형식은 V7 /recent와 같은 ChatCard.
 *
 * @param snapshot    true = 추천받을 때 저장한 제품 순위, false = 저장된 순위가 없어 지금 기준으로 다시 계산
 * @param notice      제품 안내 문구 (V3와 같은 문구)
 * @param ingredients 성분 카드 (추천 순서). 판매 중단·차단된 제품은 빠진다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendationHistoryDetailResponse(String source, Long recommendationId, Long chatResultId,
                                                  boolean snapshot, String notice, List<ChatCard> ingredients) {
}
