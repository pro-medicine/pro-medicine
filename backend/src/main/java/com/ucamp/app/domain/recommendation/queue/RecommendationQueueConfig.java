package com.ucamp.app.domain.recommendation.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

// recommendation.queue.store = memory(기본) | redis
@Slf4j
@Configuration
public class RecommendationQueueConfig {

    @Bean
    public RecommendationQueue recommendationQueue(@Value("${recommendation.queue.store:memory}") String store,
                                                   ObjectProvider<StringRedisTemplate> redis) {
        if ("redis".equalsIgnoreCase(store)) {
            log.info("recommendation queue = redis list");
            return new RedisRecommendationQueue(redis.getObject());
        }
        log.info("recommendation queue = memory");
        return new InMemoryRecommendationQueue();
    }
}
