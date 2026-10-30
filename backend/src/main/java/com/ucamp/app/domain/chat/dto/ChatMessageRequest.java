package com.ucamp.app.domain.chat.dto;

/**
 * 대화 입력 (첫 입력 또는 되묻기 답). pregnancy는 첫 메시지에서만 (임신·수유 고정 질문 답 code, V2와 같은 값)
 */
public record ChatMessageRequest(String text, String pregnancy) {
}
