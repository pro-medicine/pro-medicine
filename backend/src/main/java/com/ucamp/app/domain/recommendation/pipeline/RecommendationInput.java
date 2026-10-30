package com.ucamp.app.domain.recommendation.pipeline;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

/**
 * 추천 파이프라인의 정규화 입력. 설문 응답 + (회원이면) 프로필을 합친 값이다.
 * 값은 설문 value_code 그대로 (smoking = NONE, HALF_PACK …). 평가 러너도 이 객체를 직접 만들어 넣는다.
 * responseId, evalRunId, sessionId, chatSessionId는 기록용 메타데이터라 추천 내용(캐시 키 등)에 쓰지 않는다.
 * V2 정밀 추천은 설문 없이 프로필 + 회차 임신 답 + personalizedAnswers로 만든다 (responseId = null, sessionId = 회차).
 * V4 대화창 트랙 2는 프로필 + 대화 속 약·질환 + 대화 원문(personalizedAnswers)으로 만든다 (chatSessionId = 대화 세션).
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
        String evalRunId,
        List<PersonalizedAnswer> personalizedAnswers,
        Long sessionId,
        Long chatSessionId
) {
    public static final String MEMBER = "MEMBER";
    public static final String GUEST = "GUEST";

    /**
     * V2 맞춤 질문 답 하나 (NONE·UNKNOWN 답도 포함). topicLabel은 프롬프트에 보일 한글 주제 ("불편한 점: 쉽게 피곤해요").
     * 안전 규칙(조건 코드·금지 목록·사후 규칙·용량)은 이 값을 읽지 않는다. LLM의 성분 선택과 이유에만 쓴다.
     */
    public record PersonalizedAnswer(String topic, String topicLabel, String question, List<String> selectedLabels) {
    }

    public List<PersonalizedAnswer> personalizedAnswersOrEmpty() {
        return personalizedAnswers == null ? List.of() : personalizedAnswers;
    }

    /** V4 대화창 트랙 2 입력인지 (대화 세션에서 만든 입력) */
    public boolean isChat() {
        return chatSessionId != null;
    }

    /** V2 정밀 추천 입력인지 (맞춤 질문 회차에서 만든 입력) */
    public boolean isPersonalized() {
        return sessionId != null;
    }

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
