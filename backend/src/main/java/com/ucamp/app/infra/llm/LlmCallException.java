package com.ucamp.app.infra.llm;

import lombok.Getter;

// LLM 호출 실패. 파싱·검증 실패는 여기가 아니라 파이프라인에서 처리한다
@Getter
public class LlmCallException extends RuntimeException {

    public enum Kind {
        TIMEOUT, HTTP_ERROR, NETWORK
    }

    private final Kind kind;
    private final Integer httpStatus;
    private final Long retryAfterSeconds;
    private final long latencyMs;
    private final Long callLogId;

    public LlmCallException(Kind kind, Integer httpStatus, Long retryAfterSeconds, long latencyMs, String message,
                            Throwable cause) {
        this(kind, httpStatus, retryAfterSeconds, latencyMs, null, message, cause);
    }

    private LlmCallException(Kind kind, Integer httpStatus, Long retryAfterSeconds, long latencyMs, Long callLogId,
                             String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.retryAfterSeconds = retryAfterSeconds;
        this.latencyMs = latencyMs;
        this.callLogId = callLogId;
    }

    public LlmCallException withCallLogId(Long id) {
        return new LlmCallException(kind, httpStatus, retryAfterSeconds, latencyMs, id, getMessage(), getCause());
    }

    // 재시도해도 되는 실패: 429, 5xx, 타임아웃, 네트워크 오류
    public boolean isRetryable() {
        if (kind == Kind.TIMEOUT || kind == Kind.NETWORK) {
            return true;
        }
        return httpStatus != null && (httpStatus == 429 || httpStatus >= 500);
    }
}
