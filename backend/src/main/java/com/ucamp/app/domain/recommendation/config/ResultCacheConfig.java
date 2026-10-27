package com.ucamp.app.domain.recommendation.config;

import com.ucamp.app.domain.recommendation.cache.InMemoryResultCacheStore;
import com.ucamp.app.domain.recommendation.cache.RedisResultCacheStore;
import com.ucamp.app.domain.recommendation.cache.ResultCacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

// recommendation.cache.store = memory(기본) | redis
@Slf4j
@Configuration
public class ResultCacheConfig {

    @Bean
    public ResultCacheStore resultCacheStore(RecommendationProperties properties,
                                             ObjectProvider<StringRedisTemplate> redis, ObjectMapper objectMapper) {
        if (properties.getCache().isRedis()) {
            log.info("result cache store = redis");
            return new RedisResultCacheStore(redis.getObject(), objectMapper);
        }
        log.info("result cache store = memory");
        return new InMemoryResultCacheStore();
    }
}
