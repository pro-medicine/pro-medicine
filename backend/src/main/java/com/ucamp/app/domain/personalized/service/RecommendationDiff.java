package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 대비 변화 (설계 9-1, 모니터링 전용. 사용자에게 보이지 않음).
 * 최종 추천(제외되지 않은 성분)의 성분 코드와 순위(1부터)로 비교한다.
 * { "added": [...], "removed": [...], "rankChanged": [{"ingredient":"MAGNESIUM","from":3,"to":1}] }
 */
public record RecommendationDiff(List<String> added, List<String> removed, List<RankChange> rankChanged) {

    public record RankChange(String ingredient, int from, int to) {
    }

    public static RecommendationDiff of(RecommendationResponse baseline, RecommendationResponse current) {
        Map<String, Integer> before = ranks(baseline);
        Map<String, Integer> after = ranks(current);
        List<String> added = after.keySet().stream().filter(i -> !before.containsKey(i)).toList();
        List<String> removed = before.keySet().stream().filter(i -> !after.containsKey(i)).toList();
        List<RankChange> rank = new ArrayList<>();
        after.forEach((ingredient, to) -> {
            Integer from = before.get(ingredient);
            if (from != null && !from.equals(to)) {
                rank.add(new RankChange(ingredient, from, to));
            }
        });
        return new RecommendationDiff(added, removed, rank);
    }

    // 성분 → 순위 (sortOrder 순서대로 1, 2, 3 …)
    private static Map<String, Integer> ranks(RecommendationResponse r) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        r.items().stream()
                .sorted(Comparator.comparingInt(RecommendationResponse.Item::sortOrder))
                .forEach(i -> ranks.putIfAbsent(code(i.ingredient()), ranks.size() + 1));
        return ranks;
    }

    // V0 파이프라인은 성분을 한글 이름으로 저장한다. 이름이 성분 표의 이름(괄호 앞)과 같으면 코드로 맞춰 비교한다
    static String code(String ingredient) {
        if (ingredient == null || IngredientCode.find(ingredient).isPresent()) {
            return ingredient;
        }
        String name = ingredient.strip();
        return Arrays.stream(IngredientCode.values())
                .filter(c -> c.getNameKo().equals(name) || c.getNameKo().split(" \\(")[0].equals(name))
                .map(Enum::name).findFirst().orElse(name);
    }
}
