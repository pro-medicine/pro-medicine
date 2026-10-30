package com.ucamp.app.domain.chat.parse;

import lombok.Getter;

import java.util.List;

/** ① 구조화 실패 (LLM 호출 실패 또는 재요청 후에도 검증 실패). 화면에는 "잠시 후 다시 시도해 주세요" */
@Getter
public class ChatParseException extends RuntimeException {
    public static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
    public static final String LLM_CALL_FAILED = "LLM_CALL_FAILED";
    public static final String VALIDATION_FAIL_AFTER_REPAIR = "VALIDATION_FAIL_AFTER_REPAIR";

    private final String reason;
    private final List<Long> logIds;

    public ChatParseException(String reason, List<Long> logIds, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
        this.logIds = List.copyOf(logIds);
    }
}
