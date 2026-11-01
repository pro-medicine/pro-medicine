package com.ucamp.app.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitSurveyResponse(Long responseId, String guestToken) {
    public SubmitSurveyResponse(Long responseId) {
        this(responseId, null);
    }
}
