package com.ucamp.app.domain.chat.dto;

/**
 * 대화 다시 보기. result는 결과 스냅샷(ai_chat_results) 그대로 (결과가 없으면 null). 대화 원문은 내보내지 않는다
 */
public record ChatSessionResponse(Long chatSessionId, String status, ChatMessageResponse result) {
}
