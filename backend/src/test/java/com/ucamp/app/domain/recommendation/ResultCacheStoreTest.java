package com.ucamp.app.domain.recommendation;

import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.recommendation.cache.CachedLlmOutput;
import com.ucamp.app.domain.recommendation.cache.InMemoryResultCacheStore;
import com.ucamp.app.domain.recommendation.cache.RedisResultCacheStore;
import com.ucamp.app.domain.recommendation.cache.ResultCacheStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCacheStoreTest {
    private final CachedLlmOutput value = new CachedLlmOutput(
            List.of(new CachedLlmOutput.Item("VITAMIN_D", "햇빛", 1)), "요약", "gpt-6.1-sol");

    @Test
    void 메모리_저장소_조회와_TTL_만료() throws Exception {
        ResultCacheStore store = new InMemoryResultCacheStore();
        store.put("k", value, Duration.ofMinutes(1));
        store.put("short", value, Duration.ofMillis(1));
        Thread.sleep(5);

        assertThat(store.get("k")).contains(value);
        assertThat(store.get("short")).isEmpty();
        assertThat(store.get("none")).isEmpty();
    }

    /**
     * 실제 Redis 확인 (REDIS_HOST가 있을 때만). 예: docker run -d -p 6379:6379 redis:7 후 REDIS_HOST=localhost
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
    void Redis_저장소_조회와_TTL() throws Exception {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(System.getenv("REDIS_HOST"), 6379));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            ResultCacheStore store = new RedisResultCacheStore(template, new ObjectMapper());
            store.clear();
            store.put("k", value, Duration.ofMinutes(1));
            store.put("short", value, Duration.ofSeconds(1));
            Thread.sleep(1500);

            assertThat(store.get("k")).contains(value);
            assertThat(store.get("short")).isEmpty();
            store.clear();
            assertThat(store.get("k")).isEmpty();
        } finally {
            factory.destroy();
        }
    }

    @Test
    void Redis_연결_실패는_캐시_미스로_처리() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1));
        factory.afterPropertiesSet();
        try {
            ResultCacheStore store = new RedisResultCacheStore(new StringRedisTemplate(factory), new ObjectMapper());
            store.put("k", value, Duration.ofMinutes(1));
            assertThat(store.get("k")).isEmpty();
        } finally {
            factory.destroy();
        }
    }
}
