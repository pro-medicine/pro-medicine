package com.ucamp.app.infra.llm;

// 대화 메시지 한 개 (텍스트만). role = user | assistant
public record LlmMessage(String role, String text) {
    public static LlmMessage user(String text) {
        return new LlmMessage("user", text);
    }
}
