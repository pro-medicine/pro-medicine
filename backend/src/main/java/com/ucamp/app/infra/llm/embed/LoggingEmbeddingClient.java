package com.ucamp.app.infra.llm.embed;

import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;

/**
 * 임베딩 호출을 llm_call_logs에 남긴다 (call_tag CHAT_EMBED, input_tokens만, output_tokens는 없음).
 * 비용 컬럼은 테이블에 없으므로 기존 LLM 호출처럼 토큰으로 따로 계산한다. 입력 문장은 남기지 않는다 (건강 정보)
 */
@RequiredArgsConstructor
public class LoggingEmbeddingClient implements EmbeddingClient {
    static final String PIPELINE_VERSION = "chat-v1";

    private final EmbeddingClient delegate;
    private final LlmCallLogService logService;

    @Override
    public Embedding embed(String text) {
        long start = System.nanoTime();
        try {
            Embedding e = delegate.embed(text);
            Long id = logService.save(baseLog(e.model())
                    .inputTokens(e.inputTokens())
                    .latencyMs((int) Math.max(e.latencyMs(), elapsed(start)))
                    .outcome(LlmCallLog.SUCCESS)
                    .httpStatus(200)
                    .build());
            return e.withCallLogId(id);
        } catch (LlmCallException e) {
            Long id = logService.save(baseLog(delegate.model())
                    .latencyMs((int) Math.max(e.getLatencyMs(), elapsed(start)))
                    .outcome(e.getKind() == LlmCallException.Kind.TIMEOUT ? LlmCallLog.TIMEOUT : LlmCallLog.HTTP_ERROR)
                    .httpStatus(e.getHttpStatus())
                    .build());
            throw e.withCallLogId(id);
        }
    }

    @Override
    public String model() {
        return delegate.model();
    }

    private static LlmCallLog.LlmCallLogBuilder baseLog(String model) {
        String m = model == null ? "unknown" : model;
        return LlmCallLog.builder()
                .pipelineVersion(PIPELINE_VERSION)
                .callTag(TAG_CHAT_EMBED)
                .model(m.length() <= LlmCallLog.MODEL_LENGTH ? m : m.substring(0, LlmCallLog.MODEL_LENGTH))
                .promptVersion(PIPELINE_VERSION)
                .attemptNo(1);
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
