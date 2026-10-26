package com.ucamp.app.domain.personalized.dto;

import java.util.List;

/**
 * 회차 시작·답변·그만하기 응답 (설계 4-2 ~ 4-4).
 * question = 지금 답할 질문 (끝났으면 null). 선택지의 kind는 서버 판단용이라 내려주지 않는다.
 * progress.asked = 지금까지 낸 LLM 질문 수 (고정 질문 제외)
 */
public record PersonalizedSessionResponse(Long sessionId, String status, QuestionView question, Progress progress) {

    public record QuestionView(Long turnId, int turnNo, boolean fixed, String topic, String text, String answerType,
                               int maxSelect, List<OptionView> options) {
    }

    public record OptionView(int index, String label) {
    }

    public record Progress(int asked, int base, int max) {
    }
}
