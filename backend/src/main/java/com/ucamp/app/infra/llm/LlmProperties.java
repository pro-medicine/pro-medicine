package com.ucamp.app.infra.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * llm.* 설정. 기본값은 fake라 API 키 없이도 서버·테스트가 돈다. 실제 API는 local-llm 프로필에서만.
 * API 키는 yml에 쓰지 않는다. ANTHROPIC_API_KEY를 OS 환경변수 또는 backend/.env(Git 제외)에서 읽는다 (LlmConfig).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    /** anthropic | fake */
    private String provider = "fake";
    /** 기본 모델. 모델 ID는 Anthropic 문서의 현재 모델 표 기준 (docs/perf/pricing.md) */
    private String model = "claude-opus-5-5";
    /** STEP 08 모델 라우팅의 작은 모델 */
    private String smallModel = "claude-haiku-5-5";
    private int timeoutMs = 30000;
    /** 최신 모델은 adaptive thinking이 기본이라 thinking 토큰까지 들어갈 여유를 둔다 */
    private int maxTokens = 8000;
    /** true면 프롬프트·응답 본문을 로컬 파일(build/llm-body/)에만 남긴다. DB에는 절대 저장하지 않음 */
    private boolean logBody = false;
    private Anthropic anthropic = new Anthropic();
    private Resilience resilience = new Resilience();

    @Getter
    @Setter
    public static class Anthropic {
        private String baseUrl = "https://api.anthropic.com";
        /** anthropic-version 헤더 */
        private String version = "2023-06-01";
        /**
         * true면 tool_choice {"type":"tool"}로 도구 사용을 강제한다. Claude Opus 5.5 / Sonnet 5.5 등 최신 모델은
         * 강제 tool_choice를 400으로 거부하므로 기본값은 false (auto + strict 도구 + 프롬프트 지시).
         */
        private boolean forceToolChoice = false;
        /** output_config.effort (low|medium|high|xhigh|max). 비우면 모델 기본값 */
        private String effort;
    }

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
