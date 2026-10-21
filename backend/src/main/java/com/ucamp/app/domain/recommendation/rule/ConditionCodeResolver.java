package com.ucamp.app.domain.recommendation.rule;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 정규화 입력 → 조건 코드. 기준표 조건 행 선택(STEP 06)과 규칙 매칭(STEP 07)이 같은 코드를 쓴다.
 * 매핑은 이 클래스 한 곳에서만 관리한다.
 * (STEP 06: smoking ≠ NONE → SMOKING_CURRENT 만. STEP 07에서 전체 표로 확장)
 */
@Component
public class ConditionCodeResolver {
    public static final String SMOKING_CURRENT = "SMOKING_CURRENT";

    public Set<String> resolve(RecommendationInput input) {
        Set<String> codes = new LinkedHashSet<>();
        if (input.smoking() != null && !"NONE".equals(input.smoking())) {
            codes.add(SMOKING_CURRENT);
        }
        return codes;
    }
}
