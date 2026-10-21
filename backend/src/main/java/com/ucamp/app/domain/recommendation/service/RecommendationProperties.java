package com.ucamp.app.domain.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// recommendation.* 설정
@Getter
@Setter
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationProperties {
    /** API가 쓸 파이프라인 버전 (V0, V1 …). 평가 러너는 인자로 따로 고른다 */
    private String pipelineVersion = "V0";
    /** sync: 요청 스레드에서 LLM 호출(기존) / queue: 202 접수 후 워커가 처리 (STEP 09 비교 측정용, 둘 다 유지) */
    private String apiMode = "sync";
    private Worker worker = new Worker();

    public boolean isQueueMode() {
        return "queue".equalsIgnoreCase(apiMode);
    }

    @Getter
    @Setter
    public static class Worker {
        /** 워커 동시 처리 수 = LLM 동시 호출 상한. Anthropic 계정 rate limit을 넘지 않게 정한다 (콘솔 확인 후 조정) */
        private int concurrency = 4;
        private Duration pollTimeout = Duration.ofSeconds(1);
    }
}
