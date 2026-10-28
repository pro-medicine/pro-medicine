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
    /** sync: 요청 스레드에서 LLM 호출 / queue: 202 접수 후 워커가 처리 (비교 측정용, 둘 다 유지) */
    private String apiMode = "sync";
    private Worker worker = new Worker();
    private Cache cache = new Cache();
    private Queue queue = new Queue();

    public boolean isQueueMode() {
        return "queue".equalsIgnoreCase(apiMode);
    }

    @Getter
    @Setter
    public static class Worker {
        /** 워커 동시 처리 수 = LLM 동시 호출 상한. OpenAI 계정 rate limit을 넘지 않게 정한다 (콘솔 확인 후 조정) */
        private int concurrency = 4;
        private Duration pollTimeout = Duration.ofSeconds(1);
    }

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

    /** queue 모드의 작업 큐 */
    @Getter
    @Setter
    public static class Queue {
        /** memory | redis */
        private String store = "memory";

        public boolean isRedis() {
            return STORE_REDIS.equalsIgnoreCase(store);
        }
    }
}
