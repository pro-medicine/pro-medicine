package com.ucamp.app.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 규칙 (STEP 07 추가). 시드는 reference/domain-rules.md 3번 표 그대로 (회원 23 + 비회원 4 = 27행).
 * condition_type: HABIT, DRUG, STATUS, INGREDIENT / action: EXCLUDE, WARN, TIMING, ADJUST, ASK
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recommendation_rules")
public class RecommendationRule {

    @Id
    @Column(name = "rule_id", length = 10)
    private String ruleId;

    @Column(name = "condition_type", nullable = false, length = 15)
    private String conditionType;

    @Column(name = "condition_code", nullable = false, length = 40)
    private String conditionCode;

    @Column(name = "target_ingredient", nullable = false, length = 30)
    private String targetIngredient;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "message_ko", nullable = false, length = 300)
    private String messageKo;

    @Column(name = "guest_excluded", nullable = false)
    private boolean guestExcluded;

    @Column(name = "source_id", nullable = false, length = 20)
    private String sourceId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    public RecommendationRule(String ruleId, String conditionType, String conditionCode, String targetIngredient,
                              String action, String messageKo, boolean guestExcluded, String sourceId, Boolean active) {
        this.ruleId = ruleId;
        this.conditionType = conditionType;
        this.conditionCode = conditionCode;
        this.targetIngredient = targetIngredient;
        this.action = action;
        this.messageKo = messageKo;
        this.guestExcluded = guestExcluded;
        this.sourceId = sourceId;
        this.active = active == null || active;
    }
}
