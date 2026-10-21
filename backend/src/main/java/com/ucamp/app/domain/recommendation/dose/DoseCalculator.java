package com.ucamp.app.domain.recommendation.dose;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 용량 계산 (순수 계산 클래스: LLM·DB·Spring 의존 없음). 기준값은 호출한 쪽이 넘긴다.
 * - 기준 선택: 성분 + 성별(M/F, 없으면 ALL) + 나이 범위 + 조건(SMOKING_CURRENT 등)이 맞는 행 중 가장 구체적인 1개
 * - 현재 섭취량(KNOWN): max(권장량 − 현재, 0). 0이면 "추가 섭취 필요 없음"으로 표시하고 목록에는 남긴다
 * - 현재 섭취량 없음 또는 단위가 기준 단위와 다름(UNKNOWN): 권장량 전체 (단위 변환은 하지 않는다)
 * - 상한(값이 있을 때만): 현재 + 추천 > 상한이면 추천 = max(상한 − 현재, 0)
 */
public final class DoseCalculator {
    public static final String KNOWN = "KNOWN";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String NOTE_NO_STANDARD = "기준값 미등록";
    public static final String NOTE_INTAKE_UNKNOWN = "현재 섭취량 미반영";
    public static final String NOTE_UNIT_MISMATCH = "현재 섭취량 단위가 기준 단위와 달라 미반영";
    public static final String NOTE_NOT_NEEDED = "추가 섭취 필요 없음";
    public static final String NOTE_UPPER_LIMIT = "상한섭취량에 맞춰 조정";

    private DoseCalculator() {
    }

    public record DoseResult(BigDecimal amount, String unit, BigDecimal currentIntake, String intakeStatus,
                             String doseNote, Standard standard) {
    }

    public static DoseResult calculate(String ingredientCode, String gender, Integer age, Set<String> conditions,
                                       List<RecommendationInput.Intake> currentIntake, List<Standard> standards) {
        Optional<Standard> found = select(ingredientCode, gender, age, conditions, standards);
        if (found.isEmpty() || found.get().recommendedAmount() == null) {
            return new DoseResult(null, null, null, null, NOTE_NO_STANDARD, found.orElse(null));
        }
        Standard s = found.get();
        List<RecommendationInput.Intake> same = currentIntake.stream()
                .filter(i -> ingredientCode.equals(i.ingredient()))
                .toList();
        boolean unitMismatch = same.stream().anyMatch(i -> !sameUnit(i.unit(), s.unit()));

        BigDecimal current = null;
        String status;
        String note = null;
        BigDecimal amount;
        if (same.isEmpty() || unitMismatch) {
            status = UNKNOWN;
            note = unitMismatch ? NOTE_UNIT_MISMATCH : NOTE_INTAKE_UNKNOWN;
            amount = s.recommendedAmount();
        } else {
            status = KNOWN;
            current = same.stream().map(RecommendationInput.Intake::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            amount = s.recommendedAmount().subtract(current).max(BigDecimal.ZERO);
            if (amount.signum() == 0) {
                note = NOTE_NOT_NEEDED;
            }
        }
        if (s.upperLimit() != null) {
            BigDecimal base = current == null ? BigDecimal.ZERO : current;
            if (base.add(amount).compareTo(s.upperLimit()) > 0) {
                amount = s.upperLimit().subtract(base).max(BigDecimal.ZERO);
                note = join(note, NOTE_UPPER_LIMIT);
            }
        }
        return new DoseResult(amount.stripTrailingZeros(), s.unit(), current, status, note, s);
    }

    /** 현재 섭취 + 추천량이 상한을 넘는지. 상한을 모르면 null (평가 러너 over_limit_rate용) */
    public static Boolean exceedsUpperLimit(String ingredientCode, String gender, Integer age, Set<String> conditions,
                                            List<RecommendationInput.Intake> currentIntake, List<Standard> standards,
                                            BigDecimal amount, String unit) {
        Optional<Standard> found = select(ingredientCode, gender, age, conditions, standards);
        if (amount == null || found.isEmpty() || found.get().upperLimit() == null || unit == null
                || !sameUnit(unit, found.get().unit())) {
            return null;
        }
        BigDecimal current = currentIntake.stream()
                .filter(i -> ingredientCode.equals(i.ingredient()) && sameUnit(i.unit(), found.get().unit()))
                .map(RecommendationInput.Intake::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return current.add(amount).compareTo(found.get().upperLimit()) > 0;
    }

    static Optional<Standard> select(String ingredientCode, String gender, Integer age, Set<String> conditions,
                                     List<Standard> standards) {
        return standards.stream()
                .filter(s -> s.ingredientCode().equals(ingredientCode))
                .filter(s -> Standard.ALL.equals(s.gender()) || Objects.equals(s.gender(), gender))
                .filter(s -> age == null ? s.ageMin() <= 0 && s.ageMax() == null
                        : age >= s.ageMin() && (s.ageMax() == null || age <= s.ageMax()))
                .filter(s -> s.conditionCode() == null || conditions.contains(s.conditionCode()))
                .max(Comparator
                        .comparingInt((Standard s) -> s.conditionCode() != null ? 1 : 0)
                        .thenComparingInt(s -> Standard.ALL.equals(s.gender()) ? 0 : 1)
                        .thenComparingInt(s -> s.ageMax() == null ? Integer.MIN_VALUE : -(s.ageMax() - s.ageMin())));
    }

    static boolean sameUnit(String a, String b) {
        return a != null && b != null && a.strip().equalsIgnoreCase(b.strip());
    }

    private static String join(String a, String b) {
        return a == null ? b : a + ", " + b;
    }
}
