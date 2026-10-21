package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.recommendation.rule.RuleEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V4: 사전 필터. V3 + LLM 호출 전에 RuleEngine.preFilter로 금지 성분 목록을 만들어 프롬프트에 넣는다 (rule_pre 단계).
 * 사후 검증은 그대로 둔다 (최종 안전 책임은 사후 검증). 금지 목록은 사용자마다 달라서 사용자 메시지 뒤에 둔다
 * (STEP 08 프롬프트 캐싱에서 고정 부분 뒤에 와야 하기 때문).
 */
@Component
public class V4Pipeline extends V3Pipeline {
    public static final String VERSION = "V4";

    @Autowired
    public V4Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v4", "v4.1", false));
    }

    protected V4Pipeline(PipelineDeps deps, Options options) {
        super(deps, options);
    }

    @Override
    protected String extraUserText(RecommendationInput input) {
        List<RuleEngine.Banned> banned = deps.getStageTimer().record("rule_pre", version(),
                () -> deps.getRuleService().preFilter(input));
        if (banned.isEmpty()) {
            return null;
        }
        String list = banned.stream()
                .map(b -> "- " + b.ingredient() + ": " + b.rule().messageKo())
                .collect(Collectors.joining("\n"));
        return deps.getPromptTemplates().render("v4/banned.txt", Map.of("banned", list));
    }
}
