package com.ucamp.app.domain.chat.parse;

import java.util.List;

/**
 * ① LLM 구조화 결과 (검증 통과한 값). 안전 판단은 하지 않는다: 약·질환은 뽑기만 하고 판단은 RuleEngine·ProductSafetyFilter
 *
 * @param clarification needsClarification일 때만 (질문은 ClarificationGuard를 거친 값)
 */
public record ChatParse(String redFlag, boolean offTopic, boolean needsClarification, Clarification clarification,
                        String searchQuery, List<Medication> medications, List<String> conditions) {
    public static final String NONE = "NONE";
    public static final String EMERGENCY = "EMERGENCY";
    public static final String CRISIS = "CRISIS";

    public record Clarification(String question, List<String> options) {
    }

    public record Medication(String name, String drugClass) {
    }

    public boolean hasRedFlag() {
        return redFlag != null && !NONE.equals(redFlag);
    }

    public ChatParse withRedFlag(String level) {
        return new ChatParse(level, offTopic, needsClarification, clarification, searchQuery, medications, conditions);
    }

    public ChatParse withClarification(Clarification c) {
        return new ChatParse(redFlag, offTopic, needsClarification, c, searchQuery, medications, conditions);
    }
}
