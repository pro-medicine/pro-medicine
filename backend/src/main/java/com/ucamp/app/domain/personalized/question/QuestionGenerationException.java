package com.ucamp.app.domain.personalized.question;

import lombok.Getter;

import java.util.List;

/**
 * 질문 생성 실패 (재요청까지 검증 실패, 또는 LLM 호출 실패). 화면은 "잠시 후 다시 시도해 주세요" (PQ005).
 * reason = VALIDATION_FAIL_AFTER_REPAIR | LLM_CALL_FAILED | LLM_UNAVAILABLE
 */
@Getter
public class QuestionGenerationException extends RuntimeException {
    public static final String VALIDATION_FAIL_AFTER_REPAIR = "VALIDATION_FAIL_AFTER_REPAIR";
    public static final String LLM_CALL_FAILED = "LLM_CALL_FAILED";
    public static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";

    private final String reason;
    private final transient List<GeneratedQuestion.Attempt> attempts;

    public QuestionGenerationException(String reason, List<GeneratedQuestion.Attempt> attempts, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
        this.attempts = List.copyOf(attempts);
    }
}
