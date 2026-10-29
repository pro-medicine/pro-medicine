package com.ucamp.app.infra.llm;

import org.springframework.ai.chat.prompt.ChatOptions;

import java.time.Duration;

/**
 * Spring AI 공통 API(ChatModel, ChatOptions)로 표현되지 않는 회사별 차이만 모은 곳.
 * 다른 회사 모델로 바꿀 때는 Spring AI starter·spring.ai.* 설정과 이 구현체만 바꾸면 된다 (SpringAiLlmClient는 그대로).
 */
public interface ChatProviderSupport {

    /** 출력 토큰 한도 (회사·모델마다 파라미터 이름이 다르다. 예: OpenAI 추론 모델은 max_completion_tokens) */
    void maxOutputTokens(ChatOptions.Builder<?> options, int maxTokens);

    /** 구조화 출력의 회사별 보완 (예: strict 모드). 공통 outputSchema는 SpringAiLlmClient가 넣는다 */
    void structuredOutput(ChatOptions.Builder<?> options, String jsonSchema, boolean strict);

    /** 프롬프트 캐싱 힌트. 지원하지 않는 회사는 아무것도 하지 않는다 */
    void promptCache(ChatOptions.Builder<?> options, String cacheKey);

    /** 요청별 추론 강도. 지원하지 않는 회사는 아무것도 하지 않는다 */
    default void reasoningEffort(ChatOptions.Builder<?> options, String effort) {
    }

    /**
     * 요청별 응답 대기 시간 (llm.timeout-ms). 회사 SDK가 요청 옵션의 기본 타임아웃으로 클라이언트 설정을 덮어쓰는 경우를 막는다.
     * 지원하지 않는 회사는 아무것도 하지 않는다 (클라이언트 설정을 그대로 쓴다)
     */
    default void timeout(ChatOptions.Builder<?> options, Duration timeout) {
    }

    /** 회사 SDK 예외 → LlmCallException(상태 코드·retry-after·타임아웃 구분). 모르는 예외면 null */
    LlmCallException translate(Throwable e, long latencyMs);

    /** 응답 사용량의 입력 토큰에 캐시 토큰이 포함되는지 (OpenAI는 포함) */
    boolean inputTokensIncludeCache();
}
