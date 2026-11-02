package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.Getter;

/**
 * LLM 가드가 요청을 거부할 때 던지는 예외.
 * retryAfterSeconds > 0이면 GlobalExceptionHandler가 Retry-After 헤더를 붙인다.
 */
@Getter
public class LlmGuardException extends BusinessException {
    private final long retryAfterSeconds;

    public LlmGuardException(ErrorCode errorCode) {
        super(errorCode);
        this.retryAfterSeconds = 0;
    }

    public LlmGuardException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
