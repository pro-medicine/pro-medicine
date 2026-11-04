package com.ucamp.app.domain.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/users/me/recommendations/history 응답 (V7.1 마이페이지 "추천 기록", 최근 20건).
 * 제품은 기록마다 상세 API로 따로 불러온다
 */
public record RecommendationHistoryResponse(List<Item> items) {
    public static final String V1_SURVEY = RecentRecommendationResponse.V1_SURVEY;
    public static final String V2_PERSONALIZED = RecentRecommendationResponse.V2_PERSONALIZED;
    public static final String V4_CHAT = RecentRecommendationResponse.V4_CHAT;

    /**
     * @param source           V1_SURVEY | V2_PERSONALIZED | V4_CHAT
     * @param recommendedAt    추천 완료 시각
     * @param recommendationId ai_recommendations id (V4 트랙 1이면 null)
     * @param chatResultId     ai_chat_results id (V4 트랙 1일 때만)
     * @param concern          처음 받은 "불편한 점"
     * @param ingredients      추천 성분 코드 (추천 순서, 제외 성분 없음)
     */
    public record Item(String source, LocalDateTime recommendedAt, Long recommendationId, Long chatResultId,
                       Concern concern, List<String> ingredients) {
    }

    /**
     * @param type   LABELS(V1·V2 선택지) | TEXT(V4 문장) | NONE(없음·모름·답 없음) | EXPIRED(90일이 지나 삭제)
     * @param labels LABELS일 때 선택지 라벨 (그 밖에는 빈 목록)
     * @param text   TEXT면 처음 쓴 문장, NONE이면 "특별히 불편한 점 없음" / "잘 모르겠음" (답이 없으면 null)
     * @param answer TEXT이고 되묻기에 답했으면 그 답 (없으면 null)
     */
    public record Concern(String type, List<String> labels, String text, String answer) {
        public static final String LABELS = "LABELS";
        public static final String TEXT = "TEXT";
        public static final String NONE = "NONE";
        public static final String EXPIRED = "EXPIRED";
        public static final String NONE_TEXT = "특별히 불편한 점 없음";
        public static final String UNKNOWN_TEXT = "잘 모르겠음";

        public static Concern labels(List<String> labels) {
            return new Concern(LABELS, List.copyOf(labels), null, null);
        }

        public static Concern text(String text, String answer) {
            return new Concern(TEXT, List.of(), text, answer);
        }

        public static Concern none(String text) {
            return new Concern(NONE, List.of(), text, null);
        }

        public static Concern expired() {
            return new Concern(EXPIRED, List.of(), null, null);
        }
    }
}
