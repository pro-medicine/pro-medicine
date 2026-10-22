package com.ucamp.app.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 추천 처리 단계별 시간 측정을 한 곳에서 처리한다. 서비스 코드에 측정 코드를 흩어 놓지 않기 위함.
 * - recommendation.stage {stage, pipeline_version}: assemble, llm, parse, calc, rule_pre, rule_post, cache
 * - recommendation.total {pipeline_version}: 요청 한 건 전체
 * p50/p95/p99는 /actuator/prometheus 의 *_seconds{quantile=...} 로 본다.
 */
@Component
@RequiredArgsConstructor
public class StageTimer {
    public static final String STAGE_METRIC = "recommendation.stage";
    public static final String TOTAL_METRIC = "recommendation.total";

    public static final String ASSEMBLE = "assemble";
    public static final String CACHE = "cache";
    public static final String RULE_PRE = "rule_pre";
    public static final String LLM = "llm";
    public static final String PARSE = "parse";
    public static final String CALC = "calc";
    public static final String RULE_POST = "rule_post";

    private final MeterRegistry meterRegistry;

    public <T> T record(String stage, String pipelineVersion, Supplier<T> work) {
        return timer(STAGE_METRIC, pipelineVersion, stage).record(work);
    }

    public <T> T recordTotal(String pipelineVersion, Supplier<T> work) {
        return timer(TOTAL_METRIC, pipelineVersion, null).record(work);
    }

    private Timer timer(String name, String pipelineVersion, String stage) {
        Timer.Builder b = Timer.builder(name)
                .tag("pipeline_version", pipelineVersion == null ? "-" : pipelineVersion)
                .publishPercentiles(0.5, 0.95, 0.99);
        if (stage != null) {
            b.tag("stage", stage);
        }
        return b.register(meterRegistry);
    }
}
