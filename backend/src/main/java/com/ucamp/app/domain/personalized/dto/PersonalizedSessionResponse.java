package com.ucamp.app.domain.personalized.dto;

import java.util.List;

/**
 * 회차 시작·답변·그만하기 응답 (설계 4-2 ~ 4-4).
 * question = 지금 답할 질문 (끝났으면 null). 선택지의 kind는 서버 판단용이라 내려주지 않는다.
 * progress.asked = 지금까지 낸 LLM 질문 수 (고정 질문 제외)
 * canStop = "여기까지 답하고 결과 보기" 가능 여부 (임신 고정 질문에 답했고 LLM 질문을 1개 이상 답함)
 */
public record PersonalizedSessionResponse(Long sessionId, String status, QuestionView question, Progress progress,
                                          boolean canStop) {

    public record QuestionView(Long turnId, int turnNo, boolean fixed, String topic, String text, String answerType,
                               int maxSelect, List<OptionView> options) {
    }

    /** exclusive = "없어요"·"잘 모르겠어요"처럼 다른 선택지와 함께 고를 수 없는 선택지 (kind 자체는 보내지 않는다) */
    public record OptionView(int index, String label, boolean exclusive) {
    }

    public record Progress(int asked, int base, int max) {
    }
}
