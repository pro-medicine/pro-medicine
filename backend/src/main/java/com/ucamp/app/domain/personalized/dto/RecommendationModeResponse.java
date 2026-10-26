package com.ucamp.app.domain.personalized.dto;

import java.util.List;

/**
 * 회원의 추천 방식. V2 = 맞춤 질문, V1 = 기본 설문 (건강정보 부족).
 * missing = GENDER, BIRTH_YEAR, SENSITIVE_CONSENT, CONDITIONS_ANSWERED, MEDICATIONS_ANSWERED 중 빠진 것
 */
public record RecommendationModeResponse(String mode, List<String> missing) {
    public static final String V1 = "V1";
    public static final String V2 = "V2";

    public boolean isV2() {
        return V2.equals(mode);
    }
}
