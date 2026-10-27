package com.ucamp.app.infra.llm;

import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;

/**
 * 모든 LLM 호출을 llm_call_logs에 남기는 데코레이터.
 * HTTP 결과로 outcome(SUCCESS / TIMEOUT / HTTP_ERROR)을 정하고, 행 ID를 응답(또는 예외)에 담는다.
 * 파싱·검증 실패는 파이프라인이 markOutcome으로 고친다.
 */
@RequiredArgsConstructor
public class LoggingLlmClient implements LlmClient {
    private final LlmClient delegate;
    private final LlmCallLogService logService;

    @Override
    public LlmResponse call(LlmRequest request) {
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
                .pipelineVersion(request.pipelineVersion() == null ? "-" : request.pipelineVersion())
                .callTag(request.callTag() == null ? LlmRequest.TAG_RECOMMEND : request.callTag())
                .model(model == null ? "unknown" : truncate(model, LlmCallLog.MODEL_LENGTH))
                .promptVersion(request.promptVersion() == null ? "-" : request.promptVersion())
                .attemptNo(request.attemptNoOrDefault());
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
