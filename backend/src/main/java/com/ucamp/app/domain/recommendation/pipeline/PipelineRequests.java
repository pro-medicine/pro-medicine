package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmRequest;

import java.util.List;

// 추천 파이프라인의 LLM 요청 공통 부분 (V0와 구조화 파이프라인이 함께 쓴다)
final class PipelineRequests {
    private PipelineRequests() {
    }

    static LlmRequest.LlmRequestBuilder recommend(RecommendationInput input, String pipelineVersion,
                                                  String promptVersion, String model, String system, String user,
                                                  int maxTokens, String callTag) {
        return LlmRequest.builder()
                .model(model)
                .system(system)
                .messages(List.of(LlmMessage.user(user)))
                .maxTokens(maxTokens)
                .callTag(callTag)
                .pipelineVersion(pipelineVersion)
                .promptVersion(promptVersion)
                .responseId(input.responseId())
                .evalRunId(input.evalRunId())
                .sessionId(input.sessionId());
    }
}
