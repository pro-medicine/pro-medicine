package com.ucamp.app.infra.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * llm.* 설정 = 추천 파이프라인이 쓰는 값. 기본값은 fake라 API 키 없이도 서버·테스트가 돈다. 실제 API는 local-llm 프로필에서만.
 * 연결 설정(API 키·주소·재시도·추론 강도)은 Spring AI 표준 설정 spring.ai.* 에 둔다
 * (기본값: config/recommendation-defaults.properties, 실제 API: application-local-llm.yml).
 * API 키는 yml에 쓰지 않는다: spring.ai.openai.api-key=${OPENAI_API_KEY:} → OS 환경변수 또는 backend/.env(Git 제외).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    public static final String PROVIDER_SPRING_AI = "spring-ai";
    public static final String PROVIDER_FAKE = "fake";

    /** spring-ai(Spring AI가 만든 ChatModel 사용) | fake */
    private String provider = PROVIDER_FAKE;
    /** 기본 모델. 모델 ID는 OpenAI 문서의 모델 표 기준 (docs/perf/pricing.md) */
    private String model = "gpt-6.1-sol";
    /** STEP 08 모델 라우팅의 작은 모델 */
    private String smallModel = "gpt-6-luna";
    /** 요청 타임아웃. spring.ai.openai.chat.timeout이 이 값을 따른다 (recommendation-defaults.properties) */
    private int timeoutMs = 30000;
    /** 추론 모델은 reasoning 토큰도 이 한도(max_completion_tokens)에 포함되므로 여유를 둔다 */
    private int maxTokens = 8000;
    /** true면 프롬프트·응답 본문을 로컬 파일(build/llm-body/)에만 남긴다. DB에는 절대 저장하지 않음 */
    private boolean logBody = false;
    private Resilience resilience = new Resilience();

    /** STEP 10 장애 대응 (V10). enabled=false면 데코레이터를 붙이지 않는다 (V9 이전과 같은 동작) */
    @Getter
    @Setter
    public static class Resilience {
        private boolean enabled = false;
        /** 첫 호출 포함 최대 시도 수 (재시도 2회 = 3) */
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double backoffMultiplier = 2.0;
        private double jitter = 0.5;
        /** 429 retry-after를 따르되 이 값보다 길면 이 값까지만 기다린다 */
        private Duration maxRetryAfter = Duration.ofSeconds(10);
        private int circuitWindowSize = 20;
        private float circuitFailureRateThreshold = 50f;
        private int circuitMinimumCalls = 20;
        private Duration circuitOpenWait = Duration.ofSeconds(30);
        private int circuitHalfOpenCalls = 5;
        /** 동시 호출 제한. STEP 09 워커 동시 처리 수와 같은 값으로 둔다 */
        private int bulkheadMaxConcurrent = 20;
        private Duration bulkheadMaxWait = Duration.ofSeconds(10);
    }
}
