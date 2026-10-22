package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.recommendation.dose.DoseService;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.global.metrics.StageTimer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// LLM 출력(또는 규칙 기반 폴백 결과)의 후처리 단계. 구조화 파이프라인과 RuleOnlyPipeline이 함께 쓴다
@Component
@RequiredArgsConstructor
public class PostProcessSteps {
    private final DoseService doseService;
    private final RuleService ruleService;
    private final StageTimer stageTimer;

    /** 용량 = 기준표 권장량 − 현재 섭취량 (calc 단계) */
    public List<RecommendationItem> applyDoses(String version, RecommendationInput input, List<RecommendationItem> items) {
        return stageTimer.record(StageTimer.CALC, version, () -> doseService.applyDoses(input, items));
    }

    /** 사후 규칙 검증: 금지 성분 제외, 경고 추가 (rule_post 단계) */
    public List<RecommendationItem> applyRules(String version, RecommendationInput input, List<RecommendationItem> items) {
        return stageTimer.record(StageTimer.RULE_POST, version, () -> ruleService.applyPost(input, items));
    }
}
