package com.ucamp.app.domain.recommendation.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

// recommendation.cache.store = memory(기본) | redis
@Slf4j
@Configuration
public class ResultCacheConfig {

    @Bean
    public ResultCacheStore resultCacheStore(@Value("${recommendation.cache.store:memory}") String store,
                                             ObjectProvider<StringRedisTemplate> redis, ObjectMapper objectMapper) {
        if ("redis".equalsIgnoreCase(store)) {
            log.info("result cache store = redis");
            return new RedisResultCacheStore(redis.getObject(), objectMapper);
        }
        log.info("result cache store = memory");
        return new InMemoryResultCacheStore();
    }
}
