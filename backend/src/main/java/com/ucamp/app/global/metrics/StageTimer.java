package com.ucamp.app.global.metrics;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 추천 처리 단계별 시간 측정을 한 곳에서 처리한다. 서비스 코드에 측정 코드를 흩어 놓지 않기 위함.
 * (STEP 03: 실행만 함. STEP 04에서 Micrometer Timer 기록을 붙인다)
 */
@Component
public class StageTimer {

    public <T> T record(String stage, String pipelineVersion, Supplier<T> work) {
        return work.get();
    }
}
