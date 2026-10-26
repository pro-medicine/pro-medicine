package com.ucamp.app.infra.llm;

import lombok.Builder;

import java.util.List;

/**
 * LLM 요청. 아래 메타데이터(callTag ~ attemptNo)는 API로 보내지 않고 llm_call_logs 기록에만 쓴다.
 *
 * @param outputSchema 구조화 출력 스키마. null이면 텍스트 응답 (V0)
 * @param promptCache  true면 같은 고정 부분(시스템 프롬프트 + 스키마)끼리 캐시되도록 캐시 키를 보낸다 (V7~)
 * @param reasoningEffort 추론 강도(low 등). null이면 설정 기본값(spring.ai.openai.chat.reasoning-effort). V2 질문 생성만 low를 쓴다
 */
@Builder(toBuilder = true)
public record LlmRequest(
        String model,
        String system,
        List<LlmMessage> messages,
        int maxTokens,
        LlmOutputSchema outputSchema,
        boolean promptCache,
        String reasoningEffort,
        String callTag,
        String pipelineVersion,
        String promptVersion,
        Long responseId,
        String evalRunId,
        Long sessionId,
        int attemptNo
) {
    public static final String TAG_RECOMMEND = "RECOMMEND";
    public static final String TAG_RECOMMEND_REPAIR = "RECOMMEND_REPAIR";
    /** V2 맞춤 질문 생성과 그 재요청 */
    public static final String TAG_PQ_QUESTION = "PQ_QUESTION";
    public static final String TAG_PQ_QUESTION_REPAIR = "PQ_QUESTION_REPAIR";

    public int attemptNoOrDefault() {
        return attemptNo <= 0 ? 1 : attemptNo;
    }
}
