package com.ucamp.app.domain.recommendation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// recommendation.* 설정. 기본값은 이 클래스와 config/recommendation-defaults.properties
@Getter
@Setter
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationProperties {
    public static final String STORE_REDIS = "redis";

    /** API가 쓸 파이프라인 버전 (V0, V1 …). 기본 V4 = 안전 규칙 적용 (설정이 없는 환경에서도 규칙이 빠지지 않게). 평가 러너는 인자로 따로 고른다 */
    private String pipelineVersion = "V4";
    private Cache cache = new Cache();

    /** 결과 캐시 (V6) */
    @Getter
    @Setter
    public static class Cache {
        /** memory | redis */
        private String store = "memory";
        private Duration ttl = Duration.ofDays(7);

        public boolean isRedis() {
            return STORE_REDIS.equalsIgnoreCase(store);
        }
    }
}
