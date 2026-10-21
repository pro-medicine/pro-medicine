package com.ucamp.app.infra.llm;

/**
 * LLM을 지금 쓸 수 없음 (STEP 10): 서킷 OPEN(CallNotPermittedException)이거나 재시도 가능한 실패가 재시도 끝까지 이어진 경우.
 * 호출한 쪽(RecommendationService)은 이 예외를 받으면 규칙 기반 폴백으로 응답한다.
 * 4xx처럼 재시도 대상이 아닌 실패는 이 예외가 아니라 LlmCallException 그대로 나간다.
 */
public class LlmUnavailableException extends RuntimeException {
    public enum Reason {CIRCUIT_OPEN, RETRY_EXHAUSTED, BULKHEAD_FULL}

    private final Reason reason;

    public LlmUnavailableException(Reason reason, Throwable cause) {
        super("LLM unavailable: " + reason, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
