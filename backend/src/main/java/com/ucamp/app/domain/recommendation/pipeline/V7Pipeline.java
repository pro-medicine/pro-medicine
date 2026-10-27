package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V7 = V4 + 프롬프트 캐싱 (결과 캐시는 끈다). OpenAI는 앞부분이 같은 요청을 자동으로 캐싱하므로
 * 같은 고정 부분(시스템 프롬프트 + 스키마)끼리 묶이도록 prompt_cache_key만 보낸다 (OpenAiProviderSupport).
 * 고정 부분이 최소 캐시 길이(1024토큰)보다 짧으면 캐시가 적용되지 않는다 (프롬프트를 일부러 늘리지 않는다).
 * V4와의 차이는 prompt_cache_key뿐이라 실제 API에서 V4도 자동 캐싱이 될 수 있다.
 */
@Component
public class V7Pipeline extends StructuredPipeline {
    public V7Pipeline(PipelineDeps deps) {
        super(deps, new Options("V7", Prompt.V4,
                Feature.DOSE_CALC, Feature.RULE_POST, Feature.BANNED_LIST, Feature.PROMPT_CACHE));
    }
}
