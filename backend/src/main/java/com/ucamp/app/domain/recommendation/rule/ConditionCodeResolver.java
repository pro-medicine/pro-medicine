package com.ucamp.app.domain.recommendation.rule;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 정규화 입력 → 조건 코드. 기준표 조건 행 선택과 규칙 매칭이 같은 코드를 쓴다.
 * 매핑은 이 클래스 한 곳에서만 관리한다.
 *
 * | 입력 | 조건 코드 |
 * | smoking ≠ NONE | SMOKING_CURRENT |
 * | drinking = HIGH | DRINKING_HIGH |
 * | caffeine = HIGH | CAFFEINE_HIGH |
 * | diet_type = VEGAN | DIET_VEGAN |
 * | sun_exposure = LOW | SUN_LOW |
 * | Q-PREGNANCY = POSSIBLE | PREGNANCY_POSSIBLE |
 * | Q-PREGNANCY = PREGNANT, LACTATING | PREGNANT_OR_LACTATING |
 * | 복용 약의 drug_class | 그 값 그대로 (ACE_INHIBITOR 등) |
 * | 성별 M, 19세 이상, 현재 섭취 성분에 IRON | ADULT_MALE_TAKING_IRON |
 * | 현재 섭취 성분 | 성분 코드 그대로 (INGREDIENT 규칙용. 추천 성분은 RuleEngine이 더한다) |
 * | 비회원 | GUEST |
 */
@Component
public class ConditionCodeResolver {
    public static final String SMOKING_CURRENT = "SMOKING_CURRENT";
    public static final String DRINKING_HIGH = "DRINKING_HIGH";
    public static final String CAFFEINE_HIGH = "CAFFEINE_HIGH";
    public static final String DIET_VEGAN = "DIET_VEGAN";
    public static final String SUN_LOW = "SUN_LOW";
    public static final String PREGNANCY_POSSIBLE = "PREGNANCY_POSSIBLE";
    public static final String PREGNANT_OR_LACTATING = "PREGNANT_OR_LACTATING";
    public static final String ADULT_MALE_TAKING_IRON = "ADULT_MALE_TAKING_IRON";
    public static final String GUEST = "GUEST";
    private static final int ADULT_AGE = 19;

    public Set<String> resolve(RecommendationInput input) {
        Set<String> codes = new LinkedHashSet<>();
        if (input.smoking() != null && !"NONE".equals(input.smoking())) {
            codes.add(SMOKING_CURRENT);
        }
        if ("HIGH".equals(input.drinking())) {
            codes.add(DRINKING_HIGH);
        }
        if ("HIGH".equals(input.caffeine())) {
            codes.add(CAFFEINE_HIGH);
        }
        if ("VEGAN".equals(input.dietType())) {
            codes.add(DIET_VEGAN);
        }
        if ("LOW".equals(input.sunExposure())) {
            codes.add(SUN_LOW);
        }
        if ("POSSIBLE".equals(input.pregnancy())) {
            codes.add(PREGNANCY_POSSIBLE);
        }
        if ("PREGNANT".equals(input.pregnancy()) || "LACTATING".equals(input.pregnancy())) {
            codes.add(PREGNANT_OR_LACTATING);
        }
        for (RecommendationInput.Medication m : input.medicationsOrEmpty()) {
            if (m.drugClass() != null) {
                codes.add(m.drugClass());
            }
        }
        boolean takingIron = input.currentIntakeOrEmpty().stream().anyMatch(i -> "IRON".equals(i.ingredient()));
        Integer age = input.age();
        if ("M".equals(input.gender()) && age != null && age >= ADULT_AGE && takingIron) {
            codes.add(ADULT_MALE_TAKING_IRON);
        }
        for (RecommendationInput.Intake i : input.currentIntakeOrEmpty()) {
            codes.add(i.ingredient());
        }
        if (input.isGuest()) {
            codes.add(GUEST);
        }
        return codes;
    }
}
