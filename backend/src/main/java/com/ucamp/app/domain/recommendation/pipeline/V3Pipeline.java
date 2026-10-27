package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V3: 사후 규칙 검증. V2 + 마지막에 RuleEngine.applyPost (rule_post 단계).
 * 프롬프트는 V2와 같다 (v2.1). LLM이 원래 위반한 비율(raw_violation_rate)을 이 버전부터 따로 볼 수 있다.
 */
@Component
public class V3Pipeline extends StructuredPipeline {
    public V3Pipeline(PipelineDeps deps) {
        super(deps, new Options("V3", Prompt.V2, Feature.DOSE_CALC, Feature.RULE_POST));
    }
}
