package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
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
    public FeatureBudgetCounter featureBudgetCounter(LlmCallLogRepository logRepository) {
        return new FeatureBudgetCounter(logRepository);
    }

    @Bean
    public LlmGuard llmGuard(LlmGuardProperties properties,
                              LlmBlocklistRepository blocklistRepository,
                              LlmUserStrikeRepository strikeRepository,
                              ObjectProvider<LlmRateLimiter> rateLimiter,
                              FeatureBudgetCounter featureBudgetCounter) {
        return new LlmGuard(properties, blocklistRepository, strikeRepository,
                rateLimiter.getIfAvailable(), featureBudgetCounter);
    }
}
