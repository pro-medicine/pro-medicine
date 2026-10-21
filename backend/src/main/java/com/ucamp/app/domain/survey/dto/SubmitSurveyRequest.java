package com.ucamp.app.domain.survey.dto;

import java.math.BigDecimal;
import java.util.List;

// POST /api/v1/surveys/{surveyId}/responses 요청
public record SubmitSurveyRequest(List<Answer> answers) {

    // SINGLE/MULTI는 optionIds, NUMBER는 valueNumber, TEXT는 valueText
    public record Answer(Long questionId, List<Long> optionIds, BigDecimal valueNumber, String valueText) {
    }
}
