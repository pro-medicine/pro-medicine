package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleEngine;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 81개(회원 69 = 기본 23 + 계열 모름 12 + 진단 질환 2 + 성분 확장 32, 비회원 12 = 기본 4 + 성분 확장 8) 각각 단위 테스트.
 * 규칙 데이터는 시드(sql/seed-recommendation-rules.sql = domain-rules.md 3번 표)에서 읽고, RuleEngine은 순수 클래스로 직접 호출한다.
 */
@SpringBootTest
class RuleEngineTest {
    static final List<String> ALL_RULE_IDS = List.of("R-01", "R-02", "R-03", "R-04", "R-05", "R-06", "R-07", "R-08",
            "R-11", "R-12", "R-12b", "R-13", "R-13b", "R-14", "R-15", "R-16", "R-17", "R-17b", "R-18", "R-19",
            "R-21", "R-22", "R-23", "R-G1", "R-G2", "R-G3", "R-G4",
            "R-U01", "R-U01b", "R-U02", "R-U02b", "R-U03", "R-U04", "R-U04b", "R-U05", "R-U06", "R-U06b", "R-U07", "R-U08",
            "R-K01", "R-K02",
            // 성분 확장 (STEP 16)
            "R-30", "R-U30", "R-30b", "R-U30c", "R-30c1", "R-30c2", "R-30c3", "R-30c4", "R-31", "R-32",
            "R-33", "R-U33", "R-33b", "R-U33b", "R-33c", "R-33d", "R-34", "R-34b", "R-35", "R-U35", "R-35b", "R-35c",
            "R-36", "R-36b", "R-U36b", "R-36c", "R-36d", "R-U36d", "R-36e", "R-37", "R-38", "R-39",
            "R-G5", "R-G6", "R-G7", "R-G8", "R-G9", "R-G10", "R-G11", "R-G12");
    static final List<String> NEW_INGREDIENTS = List.of("OMEGA3", "PROBIOTICS", "CURCUMIN", "COQ10", "COLLAGEN",
            "GLUCOSAMINE", "NIACIN", "MILK_THISTLE", "LUTEIN", "VITAMIN_B6");
    static final List<String> GUEST_EXCLUDED_NEW = List.of("CURCUMIN", "COQ10", "COLLAGEN", "GLUCOSAMINE", "NIACIN",
            "MILK_THISTLE", "LUTEIN", "PROBIOTICS");

    @Autowired
    RuleService ruleService;
    RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngine(ruleService.engine().rules());
    }

    static Stream<String> ruleIds() {
        return ALL_RULE_IDS.stream();
    }

    @Test
    void 규칙은_회원_69개_비회원_12개_총_81개() {
        List<Rule> rules = engine.rules();
        assertThat(rules).extracting(Rule::ruleId).containsExactlyInAnyOrderElementsOf(ALL_RULE_IDS);
        assertThat(rules.stream().filter(Rule::guestExcluded).count()).isEqualTo(12);
        assertThat(rules.stream().filter(r -> !r.guestExcluded()).count()).isEqualTo(69);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ruleIds")
    void 규칙별_조건이_있으면_조치가_적용된다(String ruleId) {
        Rule rule = rule(ruleId);
        Set<String> conditions = Set.of(rule.conditionCode());
        List<RecommendationItem> items = List.of(item(rule.targetIngredient()));

        RuleEngine.PostResult result = engine.applyPost(items, conditions, rule.guestExcluded());

        RecommendationItem out = result.items().get(0);
        assertThat(out.appliedRuleIdsOrEmpty()).contains(ruleId);
        assertThat(result.hitRuleIds()).contains(ruleId);
        switch (rule.action()) {
            case Rule.EXCLUDE -> {
                assertThat(out.excluded()).isTrue();
                assertThat(out.warningsOrEmpty()).contains(rule.messageKo());
            }
            case Rule.ADJUST -> {
                assertThat(out.excluded()).isFalse();
                assertThat(out.warningsOrEmpty()).doesNotContain(rule.messageKo());
            }
            default -> {
                assertThat(out.excluded()).isFalse();
                assertThat(out.warningsOrEmpty()).contains(rule.messageKo());
            }
        }
        if (Rule.EXCLUDE.equals(rule.action())) {
            assertThat(engine.preFilter(conditions, rule.guestExcluded()))
                    .extracting(RuleEngine.Banned::ingredient).contains(rule.targetIngredient());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ruleIds")
    void 규칙별_조건이_없으면_적용되지_않는다(String ruleId) {
        Rule rule = rule(ruleId);

        RuleEngine.PostResult result = engine.applyPost(List.of(item(rule.targetIngredient())), Set.of(), false);

        assertThat(result.items().get(0).appliedRuleIdsOrEmpty()).doesNotContain(ruleId);
        assertThat(result.items().get(0).excluded()).isFalse();
    }

    @Test
    void 비회원_규칙은_회원에게_적용되지_않는다() {
        RuleEngine.PostResult result = engine.applyPost(List.of(item("POTASSIUM")), Set.of("GUEST"), false);

        assertThat(result.items().get(0).excluded()).isFalse();
    }

    @Test
    void INGREDIENT_규칙은_추천된_성분으로도_걸리고_제외된_성분으로는_걸리지_않는다() {
        RuleEngine.PostResult both = engine.applyPost(List.of(item("IRON"), item("CALCIUM"), item("ZINC")), Set.of(), false);
        assertThat(both.items().get(1).appliedRuleIdsOrEmpty()).containsExactly("R-21");
        assertThat(both.items().get(2).appliedRuleIdsOrEmpty()).containsExactly("R-22");

        RuleEngine.PostResult noIron = engine.applyPost(List.of(item("CALCIUM")), Set.of(), false);
        assertThat(noIron.items().get(0).appliedRuleIdsOrEmpty()).isEmpty();
    }

    @Test
    void 여러_규칙이_겹치면_모두_기록되고_제외_전_목록은_보존된다() {
        // 흡연 + ACE 억제제 + 철분 섭취 중인 남성
        Set<String> conditions = new HashSet<>(Set.of("SMOKING_CURRENT", "ACE_INHIBITOR", "ADULT_MALE_TAKING_IRON",
                "IRON", "CAFFEINE_HIGH"));
        List<RecommendationItem> items = List.of(item("VITAMIN_C"), item("BETA_CAROTENE"), item("POTASSIUM"), item("IRON"));

        RuleEngine.PostResult r = engine.applyPost(items, conditions, false);

        assertThat(r.items()).hasSize(4);
        assertThat(r.items()).filteredOn(RecommendationItem::excluded).extracting(RecommendationItem::ingredient)
                .containsExactly("BETA_CAROTENE", "POTASSIUM");
        assertThat(r.items().get(0).appliedRuleIdsOrEmpty()).containsExactly("R-01");
        assertThat(r.items().get(3).appliedRuleIdsOrEmpty()).containsExactlyInAnyOrder("R-04", "R-23");
        assertThat(engine.preFilter(conditions, false)).extracting(RuleEngine.Banned::ingredient)
                .containsExactly("BETA_CAROTENE", "POTASSIUM");
    }

    @Test
    void 혈압약_이름을_모르면_칼륨은_추천하되_상담_경고를_붙이고_이름을_알면_제외한다() {
        RuleEngine.PostResult unknown = engine.applyPost(List.of(item("POTASSIUM"), item("VITAMIN_C")),
                Set.of("ANTIHYPERTENSIVE_UNKNOWN"), false);
        RecommendationItem potassium = unknown.items().get(0);
        assertThat(potassium.excluded()).isFalse();
        assertThat(potassium.appliedRuleIdsOrEmpty()).containsExactly("R-U01");
        assertThat(potassium.warningsOrEmpty()).anyMatch(w -> w.contains("상담"));
        assertThat(unknown.items().get(1).warningsOrEmpty()).isEmpty();
        assertThat(engine.preFilter(Set.of("ANTIHYPERTENSIVE_UNKNOWN"), false)).isEmpty();

        RuleEngine.PostResult known = engine.applyPost(List.of(item("POTASSIUM")), Set.of("ARB"), false);
        assertThat(known.items().get(0).excluded()).isTrue();
    }

    // ---------- 성분 확장 (STEP 16) ----------

    @Test
    void 항응고제와_오메가3는_이름을_알든_모르든_경고() {
        assertWarn(Set.of("WARFARIN"), "OMEGA3", "R-30");
        assertWarn(Set.of("ANTICOAGULANT_UNKNOWN"), "OMEGA3", "R-U30");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pregnancyExcluded")
    void 임신_수유_중이면_제외(String ingredient) {
        assertExcluded(Set.of("PREGNANT_OR_LACTATING"), false, ingredient);
    }

    static Stream<String> pregnancyExcluded() {
        return Stream.of("LUTEIN", "MILK_THISTLE", "CURCUMIN", "COLLAGEN", "GLUCOSAMINE", "COQ10", "NIACIN");
    }

    @Test
    void 면역억제제를_먹으면_유산균_제외() {
        assertExcluded(Set.of("IMMUNOSUPPRESSANT"), false, "PROBIOTICS");
    }

    @Test
    void 약물_질환_조건별_경고() {
        assertWarn(Set.of("STATIN"), "NIACIN", "R-36");
        assertWarn(Set.of("DIABETES"), "GLUCOSAMINE", "R-35b");
        assertWarn(Set.of("KIDNEY"), "COLLAGEN", "R-34");
        assertWarn(Set.of("ANTICONVULSANT"), "VITAMIN_B6", "R-39");
        assertWarn(Set.of("INSULIN"), "COQ10", "R-33b");
        assertWarn(Set.of("CHEMOTHERAPY"), "COQ10", "R-33c");
    }

    @Test
    void 조건이_없으면_새_성분_10개_모두_통과() {
        List<RecommendationItem> items = NEW_INGREDIENTS.stream().map(RuleEngineTest::item).toList();

        RuleEngine.PostResult result = engine.applyPost(items, Set.of(), false);

        assertThat(result.items()).hasSize(10).allSatisfy(i -> {
            assertThat(i.excluded()).isFalse();
            assertThat(i.appliedRuleIdsOrEmpty()).isEmpty();
            assertThat(i.warningsOrEmpty()).isEmpty();
        });
        assertThat(engine.preFilter(Set.of(), false)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("guestExcludedNew")
    void 비회원은_회원_제외_규칙이_있는_새_성분_8개를_제외(String ingredient) {
        assertExcluded(Set.of("GUEST"), true, ingredient);
    }

    static Stream<String> guestExcludedNew() {
        return GUEST_EXCLUDED_NEW.stream();
    }

    @Test
    void 비회원도_오메가3와_비타민B6는_통과() {
        RuleEngine.PostResult result = engine.applyPost(List.of(item("OMEGA3"), item("VITAMIN_B6")),
                Set.of("GUEST"), true);

        assertThat(result.items()).allSatisfy(i -> {
            assertThat(i.excluded()).isFalse();
            assertThat(i.appliedRuleIdsOrEmpty()).isEmpty();
        });
        assertThat(engine.preFilter(Set.of("GUEST"), true)).extracting(RuleEngine.Banned::ingredient)
                .doesNotContain("OMEGA3", "VITAMIN_B6");
    }

    private void assertWarn(Set<String> conditions, String ingredient, String ruleId) {
        RecommendationItem out = engine.applyPost(List.of(item(ingredient)), conditions, false).items().get(0);
        assertThat(out.excluded()).isFalse();
        assertThat(out.appliedRuleIdsOrEmpty()).contains(ruleId);
        assertThat(out.warningsOrEmpty()).contains(rule(ruleId).messageKo());
    }

    private void assertExcluded(Set<String> conditions, boolean guest, String ingredient) {
        RecommendationItem out = engine.applyPost(List.of(item(ingredient)), conditions, guest).items().get(0);
        assertThat(out.excluded()).isTrue();
        assertThat(engine.preFilter(conditions, guest)).extracting(RuleEngine.Banned::ingredient).contains(ingredient);
    }

    private Rule rule(String id) {
        return engine.rules().stream().filter(r -> r.ruleId().equals(id)).findFirst().orElseThrow();
    }

    private static RecommendationItem item(String code) {
        return RecommendationItem.builder().ingredient(code).reason("r").sortOrder(1).build();
    }
}
