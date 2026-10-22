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
    /** 모델 라우팅(V8)의 작은 모델 */
    private String smallModel = "gpt-6-luna";
    /** 요청 타임아웃. spring.ai.openai.chat.timeout이 이 값을 따른다 (recommendation-defaults.properties) */
    private int timeoutMs = 30000;
    /** 추론 모델은 reasoning 토큰도 이 한도(max_completion_tokens)에 포함된다. OpenAI rate limit(TPM)은 이 값으로 미리 계산하므로 실제 출력(약 300)보다 너무 크게 두지 않는다 */
    private int maxTokens = 2000;
    /** true면 프롬프트·응답 본문을 로컬 파일(build/llm-body/)에만 남긴다. DB에는 절대 저장하지 않음 */
    private boolean logBody = false;
    private Resilience resilience = new Resilience();

    /** 장애 대응 (재시도·서킷 브레이커·Bulkhead·규칙 폴백). enabled=false면 ResilientLlmClient를 붙이지 않는다 */
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
        /** 동시 호출 제한. 큐 워커 동시 처리 수(recommendation.worker.concurrency)와 같은 값으로 둔다 */
        private int bulkheadMaxConcurrent = 20;
        private Duration bulkheadMaxWait = Duration.ofSeconds(10);
    }
}
