package com.ucamp.app.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 성분 기준값 (STEP 06 추가). 값은 reference/domain-rules.md 2번 표에 출처가 있는 것만 넣는다.
 * gender: M, F, ALL / age_max null이면 상한 없음 / condition_code는 STEP 07 규칙과 같은 코드 (SMOKING_CURRENT 등)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ingredient_standards")
public class IngredientStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "standard_id")
    private Long standardId;

    @Column(name = "ingredient_code", nullable = false, length = 30)
    private String ingredientCode;

    @Column(name = "gender", nullable = false, length = 5)
    private String gender;

    @Column(name = "age_min", nullable = false)
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    @Column(name = "condition_code", length = 30)
    private String conditionCode;

    @Column(name = "recommended_amount", precision = 10, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "upper_limit", precision = 10, scale = 2)
    private BigDecimal upperLimit;

    @Column(name = "unit", nullable = false, length = 10)
    private String unit;

    @Column(name = "standard_version", nullable = false, length = 20)
    private String standardVersion;

    @Column(name = "source_id", nullable = false, length = 10)
    private String sourceId;

    @Builder
    public IngredientStandard(String ingredientCode, String gender, Integer ageMin, Integer ageMax, String conditionCode,
                              BigDecimal recommendedAmount, BigDecimal upperLimit, String unit, String standardVersion,
                              String sourceId) {
        this.ingredientCode = ingredientCode;
        this.gender = gender;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.conditionCode = conditionCode;
        this.recommendedAmount = recommendedAmount;
        this.upperLimit = upperLimit;
        this.unit = unit;
        this.standardVersion = standardVersion;
        this.sourceId = sourceId;
    }
}
