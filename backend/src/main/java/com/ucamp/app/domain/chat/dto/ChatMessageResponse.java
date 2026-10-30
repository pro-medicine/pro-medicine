package com.ucamp.app.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;

import java.util.List;

/**
 * 대화 응답. type별로 쓰는 필드가 다르다 (설계 8). 화면에 보일 문장은 전부 서버 고정 문구이고,
 * LLM이 쓴 문장은 CLARIFY의 question·options뿐이다 (ClarificationGuard 검증 후)
 * - CLARIFY: question, options
 * - RED_FLAG: level(EMERGENCY | CRISIS), notice
 * - OFF_TOPIC: notice
 * - RESULT (트랙 1): track = REVIEW, message, cards, excludedItems, excludedProductCount, notice
 * - PENDING (트랙 2): track = RULE_BASED, message, recommendationId → 화면은 GET /api/v1/recommendations/{id} 폴링
 * medicationNotice: 대화에서 말한 약 중 약 마스터로 확인하지 못한 약이 있을 때
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageResponse(String type, Long chatSessionId, String track, String message, String question,
                                  List<String> options, String level, List<ChatCard> cards,
                                  List<RecommendationResponse.Item> excludedItems, Integer excludedProductCount,
                                  Long recommendationId, String notice, String medicationNotice) {
    public static final String CLARIFY = "CLARIFY";
    public static final String RED_FLAG = "RED_FLAG";
    public static final String OFF_TOPIC = "OFF_TOPIC";
    public static final String RESULT = "RESULT";
    public static final String PENDING = "PENDING";

    public static final String OFF_TOPIC_NOTICE = "영양제와 관련된 고민을 말씀해 주세요";
    public static final String REVIEW_MESSAGE = "비슷한 고민에 약사들이 추천한 성분과 제품이에요";
    public static final String RULE_BASED_MESSAGE = "비슷한 리뷰가 아직 없어 입력하신 내용으로 성분을 추천했어요";
    public static final String UNMATCHED_MEDICATION_NOTICE =
            "말씀하신 약 중 확인하지 못한 약이 있어요. 복용 전 의사·약사와 상담하세요.";

    public static ChatMessageResponse clarify(Long id, String question, List<String> options) {
        return new ChatMessageResponse(CLARIFY, id, null, null, question, options, null, null, null, null, null,
                null, null);
    }

    public static ChatMessageResponse redFlag(Long id, String level, String notice) {
        return new ChatMessageResponse(RED_FLAG, id, null, null, null, null, level, null, null, null, null, notice,
                null);
    }

    public static ChatMessageResponse offTopic(Long id) {
        return new ChatMessageResponse(OFF_TOPIC, id, null, null, null, null, null, null, null, null, null,
                OFF_TOPIC_NOTICE, null);
    }

    public static ChatMessageResponse result(Long id, List<ChatCard> cards, List<RecommendationResponse.Item> excluded,
                                             int excludedProductCount, String notice, String medicationNotice) {
        return new ChatMessageResponse(RESULT, id, "REVIEW", REVIEW_MESSAGE, null, null, null, cards, excluded,
                excludedProductCount, null, notice, medicationNotice);
    }

    public static ChatMessageResponse pending(Long id, Long recommendationId, String medicationNotice) {
        return new ChatMessageResponse(PENDING, id, "RULE_BASED", RULE_BASED_MESSAGE, null, null, null, null, null,
                null, recommendationId, null, medicationNotice);
    }
}
