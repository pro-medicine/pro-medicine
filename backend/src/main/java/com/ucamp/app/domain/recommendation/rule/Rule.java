package com.ucamp.app.domain.recommendation.rule;

import com.ucamp.app.domain.recommendation.entity.RecommendationRule;

// 규칙 한 행 (JPA와 분리한 값 객체)
public record Rule(String ruleId, String conditionType, String conditionCode, String targetIngredient, String action,
                   String messageKo, boolean guestExcluded, String sourceId) {

    public static final String EXCLUDE = "EXCLUDE";
    public static final String WARN = "WARN";
    public static final String TIMING = "TIMING";
    public static final String ADJUST = "ADJUST";
    public static final String ASK = "ASK";
    public static final String TYPE_INGREDIENT = "INGREDIENT";

    public static Rule from(RecommendationRule e) {
        return new Rule(e.getRuleId(), e.getConditionType(), e.getConditionCode(), e.getTargetIngredient(),
                e.getAction(), e.getMessageKo(), e.isGuestExcluded(), e.getSourceId());
    }
}
