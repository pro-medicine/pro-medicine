package com.ucamp.app.domain.recommendation.pipeline;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

/**
 * 추천 파이프라인의 정규화 입력. 설문 응답 + (회원이면) 프로필을 합친 값이다.
 * 값은 설문 value_code 그대로 (smoking = NONE, HALF_PACK …). 평가 러너도 이 객체를 직접 만들어 넣는다.
 * responseId, evalRunId는 기록용 메타데이터라 추천 내용(캐시 키 등)에 쓰지 않는다.
 */
@Builder(toBuilder = true)
public record RecommendationInput(
        String respondentType,
        String gender,
        Integer birthYear,
        String smoking,
        String drinking,
        String caffeine,
        String dietType,
        String sunExposure,
        BigDecimal height,
        BigDecimal weight,
        String pregnancy,
        List<String> conditions,
        List<Medication> medications,
        String sleep,
        String fatigue,
        List<String> goals,
        List<Intake> currentIntake,
        Long responseId,
        String evalRunId
) {
    public static final String MEMBER = "MEMBER";
    public static final String GUEST = "GUEST";

    /** drugClass는 약 마스터의 계열(ACE_INHIBITOR 등). 마스터에 없는 약이면 drugId·drugClass가 null이고 name만 있다 */
    public record Medication(String drugId, String drugClass, String name) {
    }

    /** 현재 섭취 중인 성분과 하루 함량 (설문 Q-SUPPLEMENTS) */
    public record Intake(String ingredient, BigDecimal amount, String unit) {
    }

    public boolean isGuest() {
        return GUEST.equals(respondentType);
    }

    public Integer age() {
        return birthYear == null ? null : Year.now().getValue() - birthYear;
    }

    public List<String> conditionsOrEmpty() {
        return conditions == null ? List.of() : conditions;
    }

    public List<Medication> medicationsOrEmpty() {
        return medications == null ? List.of() : medications;
    }

    public List<String> goalsOrEmpty() {
        return goals == null ? List.of() : goals;
    }

    public List<Intake> currentIntakeOrEmpty() {
        return currentIntake == null ? List.of() : currentIntake;
    }
}
