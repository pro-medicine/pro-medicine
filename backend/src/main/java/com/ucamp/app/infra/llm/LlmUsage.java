package com.ucamp.app.infra.llm;

// 토큰 사용량. API가 값을 주지 않으면 null
public record LlmUsage(Integer inputTokens, Integer outputTokens, Integer cacheReadTokens, Integer cacheCreationTokens) {
    public static LlmUsage empty() {
        return new LlmUsage(null, null, null, null);
    }
}
