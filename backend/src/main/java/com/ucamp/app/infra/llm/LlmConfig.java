package com.ucamp.app.infra.llm;

import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.chat.model.ChatModel;

/**
 * LlmClient 조립: [ResilientLlmClient(STEP 10, llm.resilience.enabled)] → LoggingLlmClient → (SpringAiLlmClient | Fake).
 * llm.provider 기본값은 fake. 실제 API는 local-llm 프로필(application-local-llm.yml)에서만 쓴다.
 * SpringAiLlmClient가 쓰는 ChatModel은 Spring AI 자동 설정이 spring.ai.* 설정으로 만든다
 * (spring.ai.model.chat=openai일 때만. 기본값은 none → recommendation-defaults.properties).
 * 재시도는 LoggingLlmClient 바깥이라 시도마다 llm_call_logs 행이 남는다 (attempt_no).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {
    public static final String RESILIENCE_NAME = "llm";

    @Bean
    public FakeLlmClient fakeLlmClient(ObjectMapper objectMapper) {
        return new FakeLlmClient(objectMapper);
    }

    /** 회사별 처리. 지금은 OpenAI. 다른 회사 모델로 바꾸면 이 빈을 그 회사용 구현으로 바꾼다 */
    @Bean
    public ChatProviderSupport chatProviderSupport() {
        return new OpenAiProviderSupport();
    }

    @Bean
    @Primary
    public LlmClient llmClient(LlmProperties properties, ObjectMapper objectMapper, FakeLlmClient fakeLlmClient,
                               ObjectProvider<ChatModel> chatModels, ChatProviderSupport chatProviderSupport,
                               LlmCallLogService logService, ObjectProvider<CircuitBreakerRegistry> circuitBreakers,
                               ObjectProvider<RetryRegistry> retries, ObjectProvider<BulkheadRegistry> bulkheads,
                               MeterRegistry meterRegistry) {
        LlmClient raw;
        if (LlmProperties.PROVIDER_SPRING_AI.equalsIgnoreCase(properties.getProvider())) {
            ChatModel chatModel = chatModels.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("llm.provider=spring-ai인데 Spring AI ChatModel이 없습니다. "
                        + "spring.ai.model.chat=openai 와 OPENAI_API_KEY(backend/.env)를 확인하세요 (application-local-llm.yml)");
            }
            raw = new SpringAiLlmClient(chatModel, chatProviderSupport, properties, objectMapper);
        } else {
            raw = fakeLlmClient;
        }
        LlmClient client = new LoggingLlmClient(raw, logService);
        log.info("LLM provider = {}, model = {}, resilience = {}", properties.getProvider(), properties.getModel(),
                properties.getResilience().isEnabled());
        if (!properties.getResilience().isEnabled()) {
            return client;
        }
        return resilient(client, properties.getResilience(), circuitBreakers.getIfAvailable(CircuitBreakerRegistry::ofDefaults),
                retries.getIfAvailable(RetryRegistry::ofDefaults), bulkheads.getIfAvailable(BulkheadRegistry::ofDefaults),
                meterRegistry);
    }

    static ResilientLlmClient resilient(LlmClient client, LlmProperties.Resilience r, CircuitBreakerRegistry cbRegistry,
                                        RetryRegistry retryRegistry, BulkheadRegistry bulkheadRegistry,
                                        MeterRegistry meterRegistry) {
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker(RESILIENCE_NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(r.getCircuitWindowSize())
                .minimumNumberOfCalls(r.getCircuitMinimumCalls())
                .failureRateThreshold(r.getCircuitFailureRateThreshold())
                .waitDurationInOpenState(r.getCircuitOpenWait())
                .permittedNumberOfCallsInHalfOpenState(r.getCircuitHalfOpenCalls())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(ResilientLlmClient::isRetryable)
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(e -> meterRegistry.counter("recommendation.circuit.transition",
                "to", e.getStateTransition().getToState().name()).increment());
        Retry retry = retryRegistry.retry(RESILIENCE_NAME, ResilientLlmClient.retryConfig(r));
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(RESILIENCE_NAME, BulkheadConfig.custom()
                .maxConcurrentCalls(r.getBulkheadMaxConcurrent())
                .maxWaitDuration(r.getBulkheadMaxWait())
                .build());
        return new ResilientLlmClient(client, retry, circuitBreaker, bulkhead);
    }
}
