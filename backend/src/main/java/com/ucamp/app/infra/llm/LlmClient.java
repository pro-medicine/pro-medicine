package com.ucamp.app.infra.llm;

// 모든 LLM 호출이 거치는 인터페이스. 서비스 코드는 HTTP를 직접 부르지 않는다
public interface LlmClient {

    /**
     * @throws LlmCallException 타임아웃, HTTP 오류, 네트워크 오류
     */
    LlmResponse call(LlmRequest request);
}
