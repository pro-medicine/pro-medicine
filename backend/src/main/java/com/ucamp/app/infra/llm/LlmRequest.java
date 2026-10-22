package com.ucamp.app.infra.llm;

import lombok.Builder;

import java.util.List;

/**
 * LLM 요청. 아래 메타데이터(callTag ~ attemptNo)는 API로 보내지 않고 llm_call_logs 기록에만 쓴다.
 *
 * @param temperature  null이면 보내지 않는다 (최신 모델은 기본값 외 sampling 값을 거부)
 * @param tools        tool use 정의. 비어 있으면 텍스트 응답
 * @param toolName     구조화 출력에 쓸 도구(스키마) 이름 (SpringAiLlmClient가 response_format json_schema로 보낸다)
 * @param cacheControl true면 고정 부분(tool 정의, 시스템 프롬프트) 끝에 프롬프트 캐시 지점을 표시
 */
@Builder(toBuilder = true)
public record LlmRequest(
        String model,
        String system,
        List<LlmMessage> messages,
        int maxTokens,
        Double temperature,
        List<LlmTool> tools,
        String toolName,
        boolean cacheControl,
        String callTag,
        String pipelineVersion,
        String promptVersion,
        Long responseId,
        String evalRunId,
        int attemptNo
) {
    public static final String TAG_RECOMMEND = "RECOMMEND";
    public static final String TAG_RECOMMEND_REPAIR = "RECOMMEND_REPAIR";

    public List<LlmTool> toolsOrEmpty() {
        return tools == null ? List.of() : tools;
    }

    public int attemptNoOrDefault() {
        return attemptNo <= 0 ? 1 : attemptNo;
    }
}
