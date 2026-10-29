package com.ucamp.app.domain.review.safety;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 제품 1개 안전 판단 (순수 클래스). 제품 라벨 성분 전체를 기존 RuleEngine.applyPost에 넣고 결과를
 * 찾는 성분(target) / 같이 든 성분으로 나눠 판단한다. 규칙은 새로 추가하지 않는다 (설계 D7, 5-3).
 *
 * | 경우 | 처리 |
 * | target에 EXCLUDE | 제외 (성분 추천 단계에서 이미 빠졌어야 함) |
 * | target에 WARN | 경고 + 감점 (targetWarn) |
 * | 같이 든 성분에 EXCLUDE·WARN | 제외 |
 * | TIMING·ADJUST·ASK (target·같이 든 성분 모두) | 무시 |
 * | INGREDIENT 규칙 (R-21, R-22) | 제외·감점에 쓰지 않는다. 조건 성분도 제품 안에 있으면 무시, 제품 밖(같은 추천 결과의 다른 성분, 현재 섭취 성분)에 있으면 경고만 |
 * | OTHER 성분 | 복용 약·질환 있으면 제외, 없으면 경고 |
 * | 라벨 성분 없음 | 제외 |
 */
public final class ProductSafetyFilter {
    public static final String OTHER = "OTHER";
    public static final String UNVERIFIED_WARNING = "일부 성분은 확인하지 못했어요";

    private final RuleEngine engine;
    private final Map<String, Rule> rulesById;

    public ProductSafetyFilter(RuleEngine engine) {
        this.engine = engine;
        this.rulesById = engine.rules().stream()
                .collect(Collectors.toMap(Rule::ruleId, Function.identity(), (a, b) -> a));
    }

    /** 제외 이유. 메트릭 태그(review.safety.excluded reason)에 tag 값을 쓴다 */
    public enum Reason {
        RULE("rule"), OTHER_INGREDIENT("other_ingredient"), UNKNOWN_INGREDIENTS("unknown_ingredients");

        private final String tag;

        Reason(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /**
     * 사용자 조건.
     *
     * @param conditions             ConditionCodeResolver 결과 (현재 섭취 성분 코드 포함)
     * @param hasMedicationOrDisease 복용 약 또는 진단 질환이 하나라도 있음 (OTHER 성분 판단)
     * @param recommendedIngredients 같은 추천 결과의 추천 성분 (제외되지 않은 것)
     */
    public record UserConditions(Set<String> conditions, boolean guest, boolean hasMedicationOrDisease,
                                 Collection<String> recommendedIngredients) {
    }

    /** 판단 결과. warnings는 화면에 보일 메시지 (target WARN + INGREDIENT 규칙 경고 + 확인 못 한 성분) */
    public record Result(boolean excluded, Reason reason, boolean targetWarn, List<String> warnings) {
        static Result exclude(Reason reason) {
            return new Result(true, reason, false, List.of());
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    public Result check(String target, List<String> productIngredients, UserConditions user) {
        if (productIngredients == null || productIngredients.isEmpty()) {
            return Result.exclude(Reason.UNKNOWN_INGREDIENTS);
        }
        boolean hasOther = productIngredients.contains(OTHER);
        // 라벨에 target이 없더라도(데이터 불일치) target 규칙은 확인한다
        Set<String> inProduct = new LinkedHashSet<>();
        inProduct.add(target);
        productIngredients.stream().filter(c -> !OTHER.equals(c)).forEach(inProduct::add);

        List<RecommendationItem> items = inProduct.stream()
                .map(code -> RecommendationItem.builder().ingredient(code).build()).toList();
        RuleEngine.PostResult post = engine.applyPost(items, user.conditions(), user.guest());

        boolean targetWarn = false;
        Set<String> warnings = new LinkedHashSet<>();
        for (RecommendationItem item : post.items()) {
            boolean isTarget = target.equals(item.ingredient());
            for (String ruleId : item.appliedRuleIdsOrEmpty()) {
                Rule r = rulesById.get(ruleId);
                if (r == null || Rule.TYPE_INGREDIENT.equals(r.conditionType())) {
                    continue; // INGREDIENT 규칙은 아래에서 따로 판단
                }
                boolean exclude = Rule.EXCLUDE.equals(r.action());
                boolean warn = Rule.WARN.equals(r.action());
                if (!exclude && !warn) {
                    continue; // TIMING·ADJUST·ASK 무시
                }
                if (exclude || !isTarget) {
                    return Result.exclude(Reason.RULE);
                }
                targetWarn = true;
                warnings.add(r.messageKo());
            }
        }

        if (hasOther && user.hasMedicationOrDisease()) {
            return Result.exclude(Reason.OTHER_INGREDIENT);
        }
        warnings.addAll(ingredientRuleWarnings(inProduct, user));
        if (hasOther) {
            warnings.add(UNVERIFIED_WARNING);
        }
        return new Result(false, null, targetWarn, List.copyOf(warnings));
    }

    // INGREDIENT 규칙: 대상 성분이 제품 안에 있고 조건 성분이 제품 밖(현재 섭취·같은 추천 결과의 다른 성분)에만 있을 때 경고만
    private List<String> ingredientRuleWarnings(Set<String> inProduct, UserConditions user) {
        Set<String> outside = new LinkedHashSet<>(user.conditions());
        outside.addAll(user.recommendedIngredients());
        List<String> out = new ArrayList<>();
        for (Rule r : engine.rules()) {
            if (!Rule.TYPE_INGREDIENT.equals(r.conditionType()) || Rule.ADJUST.equals(r.action())
                    || (r.guestExcluded() && !user.guest())) {
                continue;
            }
            if (inProduct.contains(r.targetIngredient()) && !inProduct.contains(r.conditionCode())
                    && outside.contains(r.conditionCode())) {
                out.add(r.messageKo());
            }
        }
        return out;
    }
}
