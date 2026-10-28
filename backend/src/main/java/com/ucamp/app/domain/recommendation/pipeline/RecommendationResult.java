package com.ucamp.app.domain.recommendation.pipeline;

import lombok.Builder;

import java.util.List;

/**
 * 파이프라인 실행 결과.
 * items에는 규칙으로 제외된 성분도 excluded=true로 남는다 (raw_violation_rate 계산용). 최종 추천은 finalItems().
 *
 * @param parseSuccess false면 재요청까지 다 해도 LLM 출력을 해석하지 못한 것 (parse_fail_rate)
 * @param llmCallLogIds 이 결과를 만드는 데 쓴 llm_call_logs 행 ID
 */
@Builder(toBuilder = true)
public record RecommendationResult(
        String pipelineVersion,
        String promptVersion,
        String model,
        boolean parseSuccess,
        List<RecommendationItem> items,
        String summary,
        List<Long> llmCallLogIds,
        boolean fallback,
        boolean cacheHit,
        String failureReason
) {
    /** 재요청까지 해도 LLM 출력을 해석하지 못한 결과 (빈 항목) */
    public static RecommendationResult parseFailure(String pipelineVersion, String promptVersion, String model,
                                                    List<Long> llmCallLogIds, String failureReason) {
        return builder().pipelineVersion(pipelineVersion).promptVersion(promptVersion).model(model)
                .llmCallLogIds(llmCallLogIds).parseSuccess(false).items(List.of()).failureReason(failureReason).build();
    }

    public List<RecommendationItem> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    public List<RecommendationItem> finalItems() {
        return itemsOrEmpty().stream().filter(i -> !i.excluded()).toList();
    }

    public List<Long> llmCallLogIdsOrEmpty() {
        return llmCallLogIds == null ? List.of() : llmCallLogIds;
    }
}
