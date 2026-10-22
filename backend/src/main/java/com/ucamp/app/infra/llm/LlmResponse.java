package com.ucamp.app.infra.llm;

import tools.jackson.databind.JsonNode;
import lombok.Builder;

/**
 * LLM 응답.
 *
 * @param text      text 블록을 이어 붙인 값 (없으면 빈 문자열)
 * @param toolInput 첫 tool_use 블록의 input (없으면 null)
 * @param callLogId LoggingLlmClient가 남긴 llm_call_logs 행 ID (기록하지 않았으면 null)
 */
@Builder(toBuilder = true)
public record LlmResponse(
        String text,
        String toolName,
        JsonNode toolInput,
        String stopReason,
        LlmUsage usage,
        long latencyMs,
        String model,
        String requestId,
        Long callLogId
) {
}
