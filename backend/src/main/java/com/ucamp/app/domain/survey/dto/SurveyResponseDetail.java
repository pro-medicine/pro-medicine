package com.ucamp.app.domain.survey.dto;

import com.ucamp.app.domain.survey.entity.RespondentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 제출된 설문 응답 한 건. answers 키는 문항 code (Q-SMOKING 등). 추천 입력 조립에 쓴다
public record SurveyResponseDetail(
        Long responseId,
        Long userId,
        RespondentType respondentType,
        boolean includesSensitive,
        LocalDateTime submittedAt,
        Map<String, AnswerValue> answers
) {
    public record AnswerValue(List<String> valueCodes, BigDecimal valueNumber, String valueText) {
        public String firstCode() {
            return valueCodes.isEmpty() ? null : valueCodes.get(0);
        }
    }

    public AnswerValue answer(String questionCode) {
        return answers.get(questionCode);
    }
}
