package com.ucamp.app.domain.recommendation.config;

import com.ucamp.app.domain.recommendation.queue.InMemoryRecommendationQueue;
import com.ucamp.app.domain.recommendation.queue.RecommendationQueue;
import com.ucamp.app.domain.recommendation.queue.RedisRecommendationQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

// recommendation.queue.store = memory(기본) | redis
@Slf4j
@Configuration
public class RecommendationQueueConfig {

    @Bean
    public RecommendationQueue recommendationQueue(RecommendationProperties properties,
                                                   ObjectProvider<StringRedisTemplate> redis) {
        if (properties.getQueue().isRedis()) {
            log.info("recommendation queue = redis list");
            return new RedisRecommendationQueue(redis.getObject());
        }
        log.info("recommendation queue = memory");
        return new InMemoryRecommendationQueue();
    }
}
