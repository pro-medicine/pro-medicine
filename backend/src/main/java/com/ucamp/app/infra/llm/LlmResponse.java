package com.ucamp.app.infra.llm;

import tools.jackson.databind.JsonNode;
import lombok.Builder;

/**
 * LLM 응답.
 *
 * @param text       텍스트 응답 (구조화 출력이면 빈 문자열)
 * @param output     구조화 출력을 파싱한 JSON (요청에 스키마가 없거나 파싱하지 못했으면 null)
 * @param stopReason 응답이 끝난 이유 (예: stop, length)
 * @param callLogId  LoggingLlmClient가 남긴 llm_call_logs 행 ID (기록하지 않았으면 null)
 */
@Builder(toBuilder = true)
public record LlmResponse(
        String text,
        JsonNode output,
        String stopReason,
        LlmUsage usage,
        long latencyMs,
        String model,
        String requestId,
        Long callLogId
) {
}
