package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V3: 사후 규칙 검증. V2 흐름 + 마지막에 RuleEngine.applyPost (rule_post 단계).
 * 프롬프트는 V2와 같다 (v2.1). LLM이 원래 위반한 비율(raw_violation_rate)을 이 버전부터 따로 볼 수 있다.
 */
@Component
public class V3Pipeline extends StructuredPipeline {
    public static final String VERSION = "V3";

    @Autowired
    public V3Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v2", "v2.1", false));
    }

    protected V3Pipeline(PipelineDeps deps, Options options) {
        super(deps, options);
    }

    @Override
    protected List<RecommendationItem> postProcess(RecommendationInput input, List<RecommendationItem> items) {
        List<RecommendationItem> dosed = deps.getStageTimer().record("calc", version(),
                () -> deps.getDoseService().applyDoses(input, items));
        return deps.getStageTimer().record("rule_post", version(),
                () -> deps.getRuleService().applyPost(input, dosed));
    }
}
