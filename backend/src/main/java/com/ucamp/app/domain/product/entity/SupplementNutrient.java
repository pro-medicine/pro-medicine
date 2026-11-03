package com.ucamp.app.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 영양제 1개의 성분 1개 함량 (14개 성분만, 함량을 아는 성분만 행이 있음).
 * - amountPerDay = 1회 함량 × 1일 섭취횟수 (홈 "오늘 먹은 영양소" 계산에 사용)
 * - unit: mg 또는 ug(= ㎍, Nutrient enum의 단위와 같은 크기)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "supplement_nutrients",
        uniqueConstraints = @UniqueConstraint(name = "uq_supplement_nutrient",
                columnNames = {"supplement_id", "nutrient_code"}))
public class SupplementNutrient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplement_nutrient_id")
    private Long supplementNutrientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplement_id", nullable = false)
    private Supplement supplement;

    // Nutrient enum 이름 (VITAMIN_D, OMEGA3 등)
    @Column(name = "nutrient_code", nullable = false, length = 30)
    private String nutrientCode;

    @Column(name = "amount_per_serving", nullable = false, precision = 14, scale = 4)
    private BigDecimal amountPerServing;

    @Column(name = "amount_per_day", nullable = false, precision = 14, scale = 4)
    private BigDecimal amountPerDay;

    @Column(name = "unit", nullable = false, length = 10)
    private String unit;
}
