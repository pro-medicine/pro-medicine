package com.ucamp.app.domain.recommendation.dose;

import com.ucamp.app.domain.recommendation.entity.IngredientStandard;

import java.math.BigDecimal;

// 기준값 한 행 (DB·JPA와 분리한 값 객체. DoseCalculator 단위 테스트에서 직접 만든다)
public record Standard(String ingredientCode, String gender, int ageMin, Integer ageMax, String conditionCode,
                       BigDecimal recommendedAmount, BigDecimal upperLimit, String unit, String standardVersion,
                       String sourceId) {

    public static final String ALL = "ALL";

    public static Standard from(IngredientStandard e) {
        return new Standard(e.getIngredientCode(), e.getGender(), e.getAgeMin(), e.getAgeMax(), e.getConditionCode(),
                e.getRecommendedAmount(), e.getUpperLimit(), e.getUnit(), e.getStandardVersion(), e.getSourceId());
    }
}
