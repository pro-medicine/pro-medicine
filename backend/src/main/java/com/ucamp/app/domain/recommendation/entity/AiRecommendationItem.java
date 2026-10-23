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

    @Builder
    public AiRecommendationItem(AiRecommendation recommendation, String ingredientName, String reason,
                                Integer sortOrder) {
        this.recommendation = recommendation;
        this.ingredientName = ingredientName;
        this.reason = reason;
        this.sortOrder = sortOrder != null ? sortOrder : 1;
    }
}
