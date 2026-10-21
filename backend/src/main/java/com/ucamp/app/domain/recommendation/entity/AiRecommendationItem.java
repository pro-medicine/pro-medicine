package com.ucamp.app.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_recommendation_items")
public class AiRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_item_id")
    private Long recommendationItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private AiRecommendation recommendation;

    @Column(name = "ingredient_name", nullable = false, length = 150)
    private String ingredientName;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    // STEP 06 추가: 기준표로 계산한 하루 추가 섭취량
    @Column(name = "recommended_amount", precision = 10, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "current_intake", precision = 10, scale = 2)
    private BigDecimal currentIntake;

    @Column(name = "intake_status", length = 10)
    private String intakeStatus;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "dose_note", length = 200)
    private String doseNote;

    // STEP 07 추가: 규칙 검증 결과. 제외된 성분도 남겨 LLM 원래 출력을 보존한다
    @Column(name = "excluded", nullable = false)
    private boolean excluded;

    @Column(name = "applied_rule_ids", length = 200)
    private String appliedRuleIds;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Builder
    public AiRecommendationItem(AiRecommendation recommendation, String ingredientName, String reason,
                                Integer sortOrder, BigDecimal recommendedAmount, BigDecimal currentIntake,
                                String intakeStatus, String unit, String doseNote, boolean excluded,
                                String appliedRuleIds, String warnings) {
        this.recommendation = recommendation;
        this.ingredientName = ingredientName;
        this.reason = reason;
        this.sortOrder = sortOrder != null ? sortOrder : 1;
        this.recommendedAmount = recommendedAmount;
        this.currentIntake = currentIntake;
        this.intakeStatus = intakeStatus;
        this.unit = unit;
        this.doseNote = doseNote;
        this.excluded = excluded;
        this.appliedRuleIds = appliedRuleIds;
        this.warnings = warnings;
    }
}
