package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.chat.dto.ChatCard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/users/me/recommendations/recent 응답 (V7 마이페이지 "최근 추천 제품 보기").
 * 성분 카드는 V4 대화창 카드(ChatCard = V1 카드 필드 + V3 제품 목록)를 그대로 쓴다. 제외 성분은 내보내지 않는다.
 *
 * @param exists           false면 추천 기록 없음 (나머지 필드 없음)
 * @param source           V1_SURVEY | V2_PERSONALIZED | V4_CHAT
 * @param recommendedAt    추천 완료 시각
 * @param recommendationId ai_recommendations id (V4 트랙 1이면 null)
 * @param chatSessionId    V4 대화 세션 id (V4가 아니면 null)
 * @param notice           제품 안내 문구 (V3와 같은 문구)
 * @param ingredients      추천 성분 카드 (추천 순서). 제품이 없는 성분도 products: []로 포함
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecentRecommendationResponse(boolean exists, String source, LocalDateTime recommendedAt,
                                           Long recommendationId, Long chatSessionId, String notice,
                                           List<ChatCard> ingredients) {
    public static final String V1_SURVEY = "V1_SURVEY";
    public static final String V2_PERSONALIZED = "V2_PERSONALIZED";
    public static final String V4_CHAT = "V4_CHAT";

    public static RecentRecommendationResponse none() {
        return new RecentRecommendationResponse(false, null, null, null, null, null, null);
    }
}
