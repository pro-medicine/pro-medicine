package com.ucamp.app.domain.personalized.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.personalized.planner.OptionKind;

/**
 * ai_question_turns.options_json의 선택지 하나. code는 고정 질문(임신·수유)에만 있다 (NOT_APPLICABLE 등)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurnOption(String label, OptionKind kind, String code) {
    public static TurnOption of(String label, OptionKind kind) {
        return new TurnOption(label, kind, null);
    }
}
