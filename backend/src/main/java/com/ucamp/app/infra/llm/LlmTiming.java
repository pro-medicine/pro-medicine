package com.ucamp.app.infra.llm;

// LLM 호출 지연 계산 (System.nanoTime 기준)
final class LlmTiming {
    private LlmTiming() {
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
