package com.ucamp.app.domain.recommendation.rule;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 규칙 검증 (LLM과 무관한 순수 클래스). 규칙 목록은 생성 시 넘긴다.
 * - 규칙은 조건 코드가 입력 조건에 있을 때 걸린다. guest_excluded 규칙은 비회원일 때만 적용
 * - INGREDIENT 규칙의 조건은 "현재 섭취 성분(조건 코드에 포함) + 이번에 추천된(제외되지 않은) 성분"
 * - EXCLUDE → excluded, WARN/TIMING/ASK → warnings에 메시지, ADJUST → 규칙 ID만 기록(용량은 기준표 조건 행으로 이미 반영)
 */
public final class RuleEngine {
    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** 사후 검증 결과: 처리된 항목 + 걸린 규칙 ID (Counter용, 걸린 순서대로) */
    public record PostResult(List<RecommendationItem> items, List<String> hitRuleIds) {
    }

    /** 사전 필터 결과: 추천하면 안 되는 성분 → 규칙 (프롬프트용) */
    public record Banned(String ingredient, Rule rule) {
    }

    public List<Banned> preFilter(Set<String> conditions, boolean guest) {
        Map<String, Banned> banned = new LinkedHashMap<>();
        for (Rule r : rules) {
            if (Rule.EXCLUDE.equals(r.action()) && applies(r, conditions, Set.of(), guest)) {
                banned.putIfAbsent(r.targetIngredient(), new Banned(r.targetIngredient(), r));
            }
        }
        return List.copyOf(banned.values());
    }

    public PostResult applyPost(List<RecommendationItem> items, Set<String> conditions, boolean guest) {
        // 1차: EXCLUDE를 먼저 적용해야 INGREDIENT 조건(추천된 성분)에서 제외된 성분을 뺄 수 있다
        Map<String, Set<String>> ruleIds = new LinkedHashMap<>();
        Map<String, List<String>> warnings = new LinkedHashMap<>();
        Set<String> excluded = new LinkedHashSet<>();
        List<String> hits = new ArrayList<>();
        Set<String> recommended = new LinkedHashSet<>();
        items.forEach(i -> recommended.add(i.ingredient()));

        for (Rule r : rules) {
            if (Rule.EXCLUDE.equals(r.action()) && recommended.contains(r.targetIngredient())
                    && applies(r, conditions, recommended, guest)) {
                excluded.add(r.targetIngredient());
                record(r, ruleIds, warnings, hits);
            }
        }
        Set<String> remaining = new LinkedHashSet<>(recommended);
        remaining.removeAll(excluded);
        for (Rule r : rules) {
            if (!Rule.EXCLUDE.equals(r.action()) && remaining.contains(r.targetIngredient())
                    && applies(r, conditions, remaining, guest)) {
                record(r, ruleIds, warnings, hits);
            }
        }

        List<RecommendationItem> out = new ArrayList<>();
        for (RecommendationItem i : items) {
            List<String> ids = new ArrayList<>(i.appliedRuleIdsOrEmpty());
            ids.addAll(ruleIds.getOrDefault(i.ingredient(), Set.of()));
            List<String> ws = new ArrayList<>(i.warningsOrEmpty());
            ws.addAll(warnings.getOrDefault(i.ingredient(), List.of()));
            out.add(i.toBuilder()
                    .excluded(i.excluded() || excluded.contains(i.ingredient()))
                    .appliedRuleIds(ids.isEmpty() ? null : List.copyOf(new LinkedHashSet<>(ids)))
                    .warnings(ws.isEmpty() ? null : List.copyOf(new LinkedHashSet<>(ws)))
                    .build());
        }
        return new PostResult(out, hits);
    }

    private static void record(Rule r, Map<String, Set<String>> ruleIds, Map<String, List<String>> warnings,
                               List<String> hits) {
        ruleIds.computeIfAbsent(r.targetIngredient(), k -> new LinkedHashSet<>()).add(r.ruleId());
        if (!Rule.ADJUST.equals(r.action())) {
            warnings.computeIfAbsent(r.targetIngredient(), k -> new ArrayList<>()).add(r.messageKo());
        }
        hits.add(r.ruleId());
    }

    static boolean applies(Rule r, Set<String> conditions, Set<String> recommended, boolean guest) {
        if (r.guestExcluded() && !guest) {
            return false;
        }
        if (Rule.TYPE_INGREDIENT.equals(r.conditionType())) {
            return conditions.contains(r.conditionCode()) || recommended.contains(r.conditionCode());
        }
        return conditions.contains(r.conditionCode());
    }

    /** 입력 조건만으로 걸리는 규칙 수 (대상 성분 추천 여부와 무관). V8 모델 라우팅의 복잡도 기준 */
    public int countApplicable(Set<String> conditions, boolean guest) {
        return (int) rules.stream().filter(r -> applies(r, conditions, Set.of(), guest)).count();
    }

    public List<Rule> rules() {
        return rules;
    }
}
