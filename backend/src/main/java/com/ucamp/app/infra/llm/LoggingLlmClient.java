package com.ucamp.app.infra.llm;

import com.ucamp.app.infra.llm.guard.FeatureBudgetCounter;
import com.ucamp.app.infra.llm.guard.LlmRateLimiter;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;

/**
 * 모든 LLM 호출을 llm_call_logs에 남기는 데코레이터.
 * HTTP 결과로 outcome(SUCCESS / TIMEOUT / HTTP_ERROR)을 정하고, 행 ID를 응답(또는 예외)에 담는다.
 * 파싱·검증 실패는 파이프라인이 markOutcome으로 고친다.
 * V5: 기능 예산 카운트도 이 계층에서 한다 (prefetch·hedge·repair 모두 포함).
 */
@RequiredArgsConstructor
public class LoggingLlmClient implements LlmClient {
    private final LlmClient delegate;
    private final LlmCallLogService logService;
    /** null이면 Redis 미사용 환경 (Redis 예산 카운트 건너뜀) */
    private final LlmRateLimiter rateLimiter;
    /** 메모리 예산 카운터 (Redis 장애 시에도 작동) */
    private final FeatureBudgetCounter featureBudgetCounter;

    @Override
    public LlmResponse call(LlmRequest request) {
        countFeatureBudget(request.feature());
        long start = System.nanoTime();
        try {
            LlmResponse response = delegate.call(request);
            LlmUsage usage = response.usage() == null ? LlmUsage.empty() : response.usage();
            Long id = logService.save(baseLog(request, response.model())
                    .inputTokens(usage.inputTokens())
                    .outputTokens(usage.outputTokens())
                    .cacheReadTokens(usage.cacheReadTokens())
                    .cacheCreationTokens(usage.cacheCreationTokens())
                    .latencyMs(latency(response.latencyMs(), start))
                    .outcome(LlmCallLog.SUCCESS)
                    .httpStatus(200)
                    .build());
            return response.toBuilder().callLogId(id).build();
        } catch (LlmCallException e) {
            Long id = logService.save(baseLog(request, request.model())
                    .latencyMs(latency(e.getLatencyMs(), start))
                    .outcome(e.getKind() == LlmCallException.Kind.TIMEOUT ? LlmCallLog.TIMEOUT : LlmCallLog.HTTP_ERROR)
                    .httpStatus(e.getHttpStatus())
                    .build());
            throw e.withCallLogId(id);
        }
    }

    private static LlmCallLog.LlmCallLogBuilder baseLog(LlmRequest request, String model) {
        return LlmCallLog.builder()
                .responseId(request.responseId())
                .evalRunId(request.evalRunId())
                .sessionId(request.sessionId())
                .pipelineVersion(request.pipelineVersion() == null ? "-" : request.pipelineVersion())
                .callTag(request.callTag() == null ? LlmRequest.TAG_RECOMMEND : request.callTag())
                .model(model == null ? "unknown" : truncate(model, LlmCallLog.MODEL_LENGTH))
                .promptVersion(request.promptVersion() == null ? "-" : request.promptVersion())
                .attemptNo(request.attemptNoOrDefault())
                .userId(request.userId())
                .feature(request.feature());
    }

    /** 기능 예산 카운트 (실제 LLM 호출 단위). 메모리 + Redis 양쪽 모두 카운트 */
    private void countFeatureBudget(String feature) {
        if (feature != null && !feature.isEmpty()) {
            if (featureBudgetCounter != null) {
                featureBudgetCounter.increment(feature);
            }
            if (rateLimiter != null) {
                rateLimiter.incrementFeatureBudget(feature);
            }
        }
    }

    // 응답에 지연이 있으면(실제 HTTP 측정값) 그 값을, 없으면 데코레이터에서 잰 값을 쓴다
    private static int latency(long reported, long startNanos) {
        long measured = LlmTiming.elapsedMs(startNanos);
        return (int) Math.max(reported, measured);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
