package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V8 = V7 + 모델 라우팅. 입력 복잡도로 작은 모델 / 기본 모델을 고른다 (ModelRouter).
 * 라우팅 결과는 llm_call_logs.model로 확인할 수 있다.
 */
@Component
public class V8Pipeline extends StructuredPipeline {
    public V8Pipeline(PipelineDeps deps) {
        super(deps, new Options("V8", Prompt.V4,
                Feature.DOSE_CALC, Feature.RULE_POST, Feature.BANNED_LIST, Feature.PROMPT_CACHE, Feature.MODEL_ROUTING));
    }
}
