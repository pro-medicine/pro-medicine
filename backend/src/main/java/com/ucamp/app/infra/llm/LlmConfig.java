package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LlmClient 조립: (Anthropic | Fake) → LoggingLlmClient.
 * llm.provider 기본값은 fake. 실제 API는 local-llm 프로필(application-local-llm.yml)에서만 쓴다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public FakeLlmClient fakeLlmClient(ObjectMapper objectMapper) {
        return new FakeLlmClient(objectMapper);
    }

    @Bean
    @Primary
    public LlmClient llmClient(LlmProperties properties, ObjectMapper objectMapper, FakeLlmClient fakeLlmClient,
                               LlmCallLogService logService) {
        LlmClient raw;
        if ("anthropic".equalsIgnoreCase(properties.getProvider())) {
            raw = new AnthropicLlmClient(properties, objectMapper);
        } else {
            raw = fakeLlmClient;
        }
        log.info("LLM provider = {}, model = {}", properties.getProvider(), properties.getModel());
        return new LoggingLlmClient(raw, logService);
    }
}
