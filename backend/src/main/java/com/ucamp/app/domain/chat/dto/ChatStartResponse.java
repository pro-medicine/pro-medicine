package com.ucamp.app.domain.chat.dto;

import java.util.List;

/**
 * 대화 시작. pregnancyQuestion은 19~50세 여성만 (나머지 null) — V2 회차 시작과 같은 조건·선택지
 */
public record ChatStartResponse(Long chatSessionId, PregnancyQuestion pregnancyQuestion) {

    public record PregnancyQuestion(String question, List<Option> options) {
    }

    public record Option(String label, String code) {
    }
}
