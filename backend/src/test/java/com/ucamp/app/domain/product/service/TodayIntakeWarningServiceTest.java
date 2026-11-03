package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.TodayNutritionResponse.IntakeWarning;
import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.recommendation.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "함께 먹을 때 주의할 점" 판정 테스트 (스프링 없이 규칙을 직접 만들어 확인).
 * 규칙 문구는 recommendation_rules의 실제 값과 같다.
 */
class TodayIntakeWarningServiceTest {

    private static final Rule IRON_CALCIUM = new Rule("R-21", "INGREDIENT", "IRON", "CALCIUM", "TIMING",
            "철분과 칼슘은 다른 시간대에 드세요", false, "S-16");
    private static final Rule IRON_ZINC = new Rule("R-22", "INGREDIENT", "IRON", "ZINC", "WARN",
            "고용량 철분은 아연 흡수를 줄일 수 있어요", false, "S-16");
    private static final Rule PREGNANT_VITA = new Rule("R-08", "STATUS", "PREGNANT_OR_LACTATING", "VITAMIN_A", "EXCLUDE",
            "임신·수유 중에는 고용량 비타민 A(레티놀)를 피하세요", false, "S-06");
    private static final Rule WARFARIN_OMEGA3 = new Rule("R-30", "DRUG", "WARFARIN", "OMEGA3", "WARN",
            "와파린과 고용량 오메가3를 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요", false, "S-21");
    private static final Rule VEGAN_B12_ASK = new Rule("R-05", "HABIT", "DIET_VEGAN", "VITAMIN_B12", "ASK",
            "비건 식단은 비타민 B12 보충이 필요한지 확인하세요", false, "S-09");
    private static final Rule GUEST_ONLY = new Rule("R-G3", "STATUS", "GUEST", "VITAMIN_A", "EXCLUDE",
            "회원 맞춤 추천에서 확인해 드려요", true, "S-06");

    private static final List<Rule> RULES = List.of(IRON_CALCIUM, IRON_ZINC, PREGNANT_VITA, WARFARIN_OMEGA3,
            VEGAN_B12_ASK, GUEST_ONLY);

    @Test
    @DisplayName("성분·임신·약 조건이 맞는 규칙을 피하세요 > 주의 > 시간 간격 순으로 보여준다")
    void allKinds() {
        Map<Nutrient, Set<String>> slots = slots(
                Nutrient.IRON, Set.of("BREAKFAST"), Nutrient.CALCIUM, Set.of("BREAKFAST"),
                Nutrient.ZINC, Set.of("DINNER"), Nutrient.VITAMIN_A, Set.of("BREAKFAST"),
                Nutrient.OMEGA3, Set.of("LUNCH"), Nutrient.VITAMIN_B12, Set.of("LUNCH"));

        List<IntakeWarning> r = TodayIntakeWarningService.evaluate(RULES,
                Set.of("PREGNANT_OR_LACTATING", "WARFARIN", "DIET_VEGAN"), Map.of("WARFARIN", "와파린정"), slots);

        assertThat(r).extracting(IntakeWarning::ruleId).containsExactly("R-08", "R-22", "R-30", "R-21");
        assertThat(r.get(0).pair()).containsExactly("비타민 A", "임신·수유");
        assertThat(r.get(0).nutrients()).containsExactly("VITAMIN_A");
        assertThat(r.get(2).pair()).containsExactly("오메가3", "와파린정");   // 사용자가 입력한 약 이름
        assertThat(r.get(3).pair()).containsExactly("철", "칼슘");
        assertThat(r.get(3).nutrients()).containsExactly("IRON", "CALCIUM");
        assertThat(r.get(3).note()).isEqualTo("지금 둘 다 아침에 드시고 있어요");
        // ASK(비건 B12)와 비회원 전용 규칙은 보여주지 않는다
        assertThat(r).extracting(IntakeWarning::ruleId).doesNotContain("R-05", "R-G3");
    }

    @Test
    @DisplayName("성분끼리 시간 간격 규칙은 다른 끼니에 먹고 있으면 보여주지 않는다")
    void timingOnlyWhenSameSlot() {
        List<IntakeWarning> r = TodayIntakeWarningService.evaluate(List.of(IRON_CALCIUM), Set.of(), Map.of(),
                slots(Nutrient.IRON, Set.of("BREAKFAST"), Nutrient.CALCIUM, Set.of("DINNER")));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("대상 성분을 오늘 먹지 않거나 조건이 없으면 걸리지 않는다")
    void notApplicable() {
        // 오메가3를 먹지만 와파린 조건이 없음 / 철만 먹고 아연은 안 먹음
        List<IntakeWarning> r = TodayIntakeWarningService.evaluate(RULES, Set.of(), Map.of(),
                slots(Nutrient.OMEGA3, Set.of("LUNCH"), Nutrient.IRON, Set.of("BREAKFAST")));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("약 이름을 모르면 '복용 중인 약'으로 보여준다")
    void unknownMedicationName() {
        List<IntakeWarning> r = TodayIntakeWarningService.evaluate(List.of(WARFARIN_OMEGA3), Set.of("WARFARIN"),
                Map.of(), slots(Nutrient.OMEGA3, Set.of("LUNCH")));

        assertThat(r).singleElement().satisfies(w -> assertThat(w.pair()).containsExactly("오메가3", "복용 중인 약"));
    }

    private static Map<Nutrient, Set<String>> slots(Object... pairs) {
        Map<Nutrient, Set<String>> map = new EnumMap<>(Nutrient.class);
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            Set<String> s = (Set<String>) pairs[i + 1];
            map.put((Nutrient) pairs[i], s);
        }
        return map;
    }
}
