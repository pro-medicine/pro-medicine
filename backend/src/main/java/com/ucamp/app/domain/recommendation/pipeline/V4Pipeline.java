package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V4: 사전 필터. V3 + LLM 호출 전에 RuleEngine.preFilter로 금지 성분 목록을 만들어 사용자 메시지 뒤에 넣는다 (rule_pre 단계).
 * 사후 검증은 그대로 둔다 (최종 안전 책임은 사후 검증).
 */
@Component
public class V4Pipeline extends StructuredPipeline {
    public V4Pipeline(PipelineDeps deps) {
        super(deps, new Options("V4", Prompt.V4, Feature.DOSE_CALC, Feature.RULE_POST, Feature.BANNED_LIST));
    }
}
