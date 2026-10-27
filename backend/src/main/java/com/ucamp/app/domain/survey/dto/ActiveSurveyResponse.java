package com.ucamp.app.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

// GET /api/v1/surveys/active 응답
public record ActiveSurveyResponse(
        Long surveyId,
        String version,
        String respondentType,
        boolean needsConfirm,
        List<Question> questions
) {
    public record Question(
            Long questionId,
            String code,
            String text,
            String answerType,
            boolean sensitive,
            List<Option> options,
            @JsonInclude(JsonInclude.Include.NON_NULL) Prefill prefill
    ) {
    }

    public record Option(Long optionId, String valueCode, String label) {
    }

    // 회원 프로필에서 미리 채운 값. 선택형은 optionIds, 숫자형은 valueNumber, TEXT는 valueText(JSON)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prefill(List<Long> optionIds, BigDecimal valueNumber, String valueText) {
    }
}
