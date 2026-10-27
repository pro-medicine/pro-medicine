package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.infra.llm.LlmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * V8 모델 라우팅. 기준은 단순하게 시작한다:
 * - 복잡(기본 모델): 복용 약 1개 이상, 또는 질환 1개 이상, 또는 입력에 걸리는 규칙 3개 이상
 * - 그 외: 작은 모델 (llm.small-model)
 */
@Component
@RequiredArgsConstructor
public class ModelRouter {
    static final int COMPLEX_RULE_COUNT = 3;

    private final LlmProperties llmProperties;
    private final RuleService ruleService;

    public String route(RecommendationInput input) {
        return isComplex(input) ? llmProperties.getModel() : llmProperties.getSmallModel();
    }

    public boolean isComplex(RecommendationInput input) {
        return !input.medicationsOrEmpty().isEmpty()
                || !input.conditionsOrEmpty().isEmpty()
                || ruleService.countApplicable(input) >= COMPLEX_RULE_COUNT;
    }
}
