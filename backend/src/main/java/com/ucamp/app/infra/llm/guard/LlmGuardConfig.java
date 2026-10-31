package com.ucamp.app.infra.llm.guard;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(LlmGuardProperties.class)
public class LlmGuardConfig {

    @Bean
    public LlmRateLimiter llmRateLimiter(ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = redis.getIfAvailable();
        return template != null ? new LlmRateLimiter(template) : null;
    }

    @Bean
    public LlmGuard llmGuard(LlmGuardProperties properties,
                              LlmBlocklistRepository blocklistRepository,
                              ObjectProvider<LlmRateLimiter> rateLimiter) {
        return new LlmGuard(properties, blocklistRepository, rateLimiter.getIfAvailable());
    }
}
