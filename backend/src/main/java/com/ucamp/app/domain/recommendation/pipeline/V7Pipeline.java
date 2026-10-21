package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * V7 = V4 + Claude 프롬프트 캐싱 (결과 캐시는 끈다). 변하지 않는 부분(tool 정의 → 시스템 프롬프트)의 끝에
 * cache_control을 표시한다. 사용자마다 바뀌는 입력과 금지 성분 목록은 사용자 메시지라 캐시 지점 뒤에 온다.
 * 고정 부분이 모델의 최소 캐시 길이보다 짧으면 캐시가 적용되지 않는다 (프롬프트를 일부러 늘리지 않는다).
 */
@Component
public class V7Pipeline extends V4Pipeline {
    public static final String VERSION = "V7";

    @Autowired
    public V7Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v4", "v4.1", false));
    }

    protected V7Pipeline(PipelineDeps deps, Options options) {
        super(deps, options);
    }

    @Override
    protected boolean promptCache() {
        return true;
    }
}
