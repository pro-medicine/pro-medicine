package com.ucamp.app.infra.llm;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.InterruptedIOException;
import java.util.List;

/**
 * OpenAI(Spring AI OpenAiChatModel, 공식 openai-java SDK)의 회사별 처리.
 * - 출력 토큰: 추론 모델은 max_tokens를 거부하므로 max_completion_tokens를 쓴다
 * - 구조화 출력: response_format json_schema + strict. gpt-6.1-sol은 Chat Completions에서 tool calling을 지원하지 않아 이 방식을 쓴다
 * - 프롬프트 캐싱: 자동(최소 1,024토큰). prompt_cache_key로 같은 고정 부분끼리 묶는다
 * - 예외: SDK의 OpenAIServiceException(상태 코드·헤더) / OpenAIIoException(타임아웃·네트워크)
 */
public class OpenAiProviderSupport implements ChatProviderSupport {

    @Override
    public void maxOutputTokens(ChatOptions.Builder<?> options, int maxTokens) {
        openAi(options).maxCompletionTokens(maxTokens);
    }

    @Override
    public void structuredOutput(ChatOptions.Builder<?> options, String jsonSchema, boolean strict) {
        openAi(options).responseFormat(OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(jsonSchema)
                .strict(strict)
                .build());
    }

    @Override
    public void promptCache(ChatOptions.Builder<?> options, String cacheKey) {
        openAi(options).promptCacheKey(cacheKey);
    }

    @Override
    public LlmCallException translate(Throwable e, long latencyMs) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof OpenAIServiceException se) {
                List<String> retryAfter = se.headers().values("retry-after");
                return new LlmCallException(LlmCallException.Kind.HTTP_ERROR, se.statusCode(),
                        retryAfter.isEmpty() ? null : parseLong(retryAfter.get(0)), latencyMs,
                        "OpenAI API HTTP " + se.statusCode() + " " + se.code().orElse(se.type().orElse("")), e);
            }
            if (t instanceof InterruptedIOException) {
                return new LlmCallException(LlmCallException.Kind.TIMEOUT, null, null, latencyMs, "LLM timeout", e);
            }
            if (t instanceof OpenAIIoException && !hasCause(t, InterruptedIOException.class)) {
                return new LlmCallException(LlmCallException.Kind.NETWORK, null, null, latencyMs,
                        String.valueOf(t.getMessage()), e);
            }
        }
        return null;
    }

    @Override
    public boolean inputTokensIncludeCache() {
        return true;
    }

    private static OpenAiChatOptions.Builder openAi(ChatOptions.Builder<?> options) {
        if (options instanceof OpenAiChatOptions.Builder b) {
            return b;
        }
        throw new IllegalStateException("OpenAiProviderSupport는 OpenAiChatModel의 옵션에만 쓸 수 있습니다: "
                + options.getClass().getName() + " (spring.ai.model.chat 설정과 ChatProviderSupport 빈을 확인)");
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
