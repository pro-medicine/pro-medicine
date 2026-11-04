package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.TodayNutritionResponse.IntakeWarning;
import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 홈 "오늘 먹은 영양소"의 "함께 먹을 때 주의할 점".
 * 오늘 먹는 영양제의 성분 + 설문 조건(임신·흡연·복용 약·질환)을 recommendation_rules 규칙에 맞춰 본다.
 *
 * 규칙이 걸리는 기준은 추천 기능의 RuleEngine.applies와 같다 (회원 기준이라 비회원 전용 규칙은 빼고):
 * - 대상 성분(target_ingredient)을 오늘 먹고 있고
 * - 조건(condition_code)이 내 조건 코드에 있거나, 성분 규칙(INGREDIENT)이면 그 성분도 오늘 먹고 있을 때
 * 보여주는 종류: EXCLUDE(피하세요) > WARN(주의) > TIMING(시간 간격). ASK·ADJUST는 보여주지 않는다.
 * 성분끼리의 TIMING(예: 철 + 칼슘)은 두 성분을 같은 끼니에 먹을 때만 보여준다 (이미 따로 먹으면 안내할 필요 없음).
 */
@Service
@RequiredArgsConstructor
public class TodayIntakeWarningService {

    static final List<String> SHOWN_ACTIONS = List.of(Rule.EXCLUDE, Rule.WARN, Rule.TIMING);

    // 성분이 아닌 조건 코드를 화면에 보여줄 이름 (약 분류는 사용자가 입력한 약 이름을 먼저 쓴다)
    private static final Map<String, String> CONDITION_LABELS = Map.ofEntries(
            Map.entry("PREGNANT_OR_LACTATING", "임신·수유"),
            Map.entry("PREGNANCY_POSSIBLE", "임신 가능성"),
            Map.entry("SMOKING_CURRENT", "흡연"),
            Map.entry("DRINKING_HIGH", "잦은 음주"),
            Map.entry("CAFFEINE_HIGH", "카페인"),
            Map.entry("DIET_VEGAN", "비건 식단"),
            Map.entry("SUN_LOW", "햇빛 노출 적음"),
            Map.entry("KIDNEY", "신장 질환"),
            Map.entry("DIABETES", "당뇨"),
            Map.entry("HYPERTENSION", "고혈압"),
            Map.entry("THYROID", "갑상선 질환"));

    private static final Map<String, String> SLOT_NAMES = Map.of(
            "BREAKFAST", "아침", "LUNCH", "점심", "DINNER", "저녁");

    private final RuleService ruleService;

    /**
     * @param profile          설문으로 만든 사용자 정보 (조건 코드, 약 이름)
     * @param slotsByNutrient  오늘 먹는 성분 → 그 성분이 든 영양제를 먹는 끼니 (BREAKFAST 등)
     */
    public List<IntakeWarning> warnings(PersonalNutrientStandardService.Profile profile,
                                        Map<Nutrient, Set<String>> slotsByNutrient) {
        return evaluate(ruleService.engine().rules(), profile.conditions(), profile.medicationNames(),
                slotsByNutrient);
    }

    /** 규칙 판정 (DB·스프링 없이 테스트할 수 있게 분리) */
    static List<IntakeWarning> evaluate(List<Rule> rules, Set<String> conditions, Map<String, String> medicationNames,
                                        Map<Nutrient, Set<String>> slotsByNutrient) {
        Set<String> taken = slotsByNutrient.keySet().stream().map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<IntakeWarning> result = new ArrayList<>();
        Set<String> seenMessages = new LinkedHashSet<>();

        for (Rule r : rules) {
            if (r.guestExcluded() || !SHOWN_ACTIONS.contains(r.action())) {
                continue;
            }
            Nutrient target = toNutrient(r.targetIngredient());
            if (target == null || !taken.contains(target.name())) {
                continue;   // 오늘 먹지 않는 성분에 대한 규칙
            }
            boolean ingredientRule = Rule.TYPE_INGREDIENT.equals(r.conditionType());
            Nutrient conditionNutrient = ingredientRule ? toNutrient(r.conditionCode()) : null;
            boolean conditionMet = ingredientRule
                    ? conditionNutrient != null && conditionNutrient != target && taken.contains(conditionNutrient.name())
                    : conditions.contains(r.conditionCode());
            if (!conditionMet) {
                continue;
            }

            String note = null;
            if (ingredientRule && Rule.TIMING.equals(r.action())) {
                // 두 성분을 같은 끼니에 먹을 때만 시간 간격 안내
                Set<String> same = sameSlots(slotsByNutrient.get(conditionNutrient), slotsByNutrient.get(target));
                if (same.isEmpty()) {
                    continue;
                }
                note = "지금 둘 다 " + same.stream().map(s -> SLOT_NAMES.getOrDefault(s, s))
                        .collect(Collectors.joining("·")) + "에 드시고 있어요";
            }
            if (!seenMessages.add(r.messageKo())) {
                continue;   // 같은 문구는 한 번만
            }

            List<String> pair = new ArrayList<>();
            List<String> nutrients = new ArrayList<>();
            if (conditionNutrient != null) {
                pair.add(conditionNutrient.getDisplayName());
                nutrients.add(conditionNutrient.name());
            }
            pair.add(target.getDisplayName());
            nutrients.add(target.name());
            if (conditionNutrient == null) {
                pair.add(conditionLabel(r.conditionCode(), medicationNames));
            }
            result.add(new IntakeWarning(r.ruleId(), r.action(), r.messageKo(), pair, nutrients, note));
        }
        result.sort(Comparator.comparingInt(w -> SHOWN_ACTIONS.indexOf(w.kind())));
        return result;
    }

    private static Set<String> sameSlots(Set<String> a, Set<String> b) {
        if (a == null || b == null) {
            return Set.of();
        }
        Set<String> same = new LinkedHashSet<>(List.of("BREAKFAST", "LUNCH", "DINNER"));
        same.retainAll(a);
        same.retainAll(b);
        return same;
    }

    private static String conditionLabel(String code, Map<String, String> medicationNames) {
        if (medicationNames.containsKey(code)) {
            return medicationNames.get(code);
        }
        return CONDITION_LABELS.getOrDefault(code, "복용 중인 약");
    }

    private static Nutrient toNutrient(String code) {
        if (code == null) {
            return null;
        }
        try {
            return Nutrient.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
