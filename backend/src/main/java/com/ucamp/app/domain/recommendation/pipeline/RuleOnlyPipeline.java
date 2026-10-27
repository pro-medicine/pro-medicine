package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 규칙 기반 폴백. LLM 없이 조건 코드에 걸리는 ADJUST 규칙과 R-05·R-06의 대상 성분만 추천한다.
 * R-23처럼 "확인"을 위한 ASK 규칙은 추천이 아니라 경고로만 쓴다. 규칙으로 설명할 수 없는 성분은 넣지 않는다.
 * 용량은 기준표로 계산하고 사후 규칙 검증을 적용한다. 결과는 fallback = true, model = RULE_ONLY.
 */
@Component
@RequiredArgsConstructor
public class RuleOnlyPipeline implements RecommendationPipeline {
    public static final String VERSION = "RULE_ONLY";
    public static final String NOTICE = "AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요";
    /** ADJUST 외에 추천으로 쓰는 규칙 (measurement.md mustInclude 정의와 같다) */
    static final Set<String> RECOMMEND_RULE_IDS = Set.of("R-05", "R-06");

    private final RuleService ruleService;
    private final ConditionCodeResolver conditionCodeResolver;
    private final PostProcessSteps postProcessSteps;

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public RecommendationResult run(RecommendationInput input) {
        Set<String> conditions = conditionCodeResolver.resolve(input);
        Map<String, Rule> picked = new LinkedHashMap<>();
        for (Rule r : ruleService.engine().rules()) {
            boolean recommendRule = Rule.ADJUST.equals(r.action()) || RECOMMEND_RULE_IDS.contains(r.ruleId());
            if (recommendRule && conditions.contains(r.conditionCode()) && (!r.guestExcluded() || input.isGuest())) {
                picked.putIfAbsent(r.targetIngredient(), r);
            }
        }
        List<RecommendationItem> items = new ArrayList<>();
        int order = 1;
        for (Rule r : picked.values()) {
            items.add(RecommendationItem.builder()
                    .ingredient(r.targetIngredient())
                    .reason(r.messageKo())
                    .sortOrder(order++)
                    .build());
        }
        List<RecommendationItem> checked = postProcessSteps.applyRules(VERSION, input,
                postProcessSteps.applyDoses(VERSION, input, items));
        return RecommendationResult.builder()
                .pipelineVersion(VERSION)
                .promptVersion("-")
                .model(VERSION)
                .parseSuccess(true)
                .items(checked)
                .summary(NOTICE)
                .llmCallLogIds(List.of())
                .fallback(true)
                .build();
    }
}
