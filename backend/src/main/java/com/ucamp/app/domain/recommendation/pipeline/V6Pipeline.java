package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V6 = V4 + 결과 캐시. 정규화 입력(CacheKeyFactory.KEY_FIELDS)이 같으면 LLM을 다시 부르지 않고 이전 LLM 출력을 쓴다.
 * 용량 계산과 규칙 검증은 캐시 적중이어도 매번 다시 한다. 프롬프트 캐싱은 끈다 (효과를 따로 재기 위해).
 */
@Component
public class V6Pipeline extends V4Pipeline {
    public static final String VERSION = "V6";

    public V6Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v4", "v4.1", false));
    }

    @Override
    protected boolean resultCache() {
        return true;
    }
}
