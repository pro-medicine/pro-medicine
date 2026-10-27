package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.dose.DoseCalculator;
import com.ucamp.app.domain.recommendation.dose.DoseCalculator.DoseResult;
import com.ucamp.app.domain.recommendation.dose.Standard;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput.Intake;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoseCalculator 단위 테스트.
 * TEST ONLY: 기준값은 계산 로직 확인용 가짜 값이다 (standards/test-only-standards.json). 실제 기준값이 아니다.
 */
class DoseCalculatorTest {
    // TEST ONLY 가짜 기준값: src/test/resources/standards/test-only-standards.json
    private static final List<Standard> TEST_ONLY = loadTestOnly();

    private static List<Standard> loadTestOnly() {
        try (var in = DoseCalculatorTest.class.getResourceAsStream("/standards/test-only-standards.json")) {
            var root = new ObjectMapper().readTree(in);
            return new ObjectMapper().convertValue(root.get("standards"), new TypeReference<List<Standard>>() {
            });
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Standard std(String code, String gender, int min, Integer max, String cond, String rec, String ul,
                                String unit) {
        return new Standard(code, gender, min, max, cond, rec == null ? null : new BigDecimal(rec),
                ul == null ? null : new BigDecimal(ul), unit, "TEST_ONLY", "TEST");
    }

    private static DoseResult calc(String code, String gender, int age, Set<String> cond, List<Intake> intake) {
        return DoseCalculator.calculate(code, gender, age, cond, intake, TEST_ONLY);
    }

    private static Intake intake(String code, String amount, String unit) {
        return new Intake(code, new BigDecimal(amount), unit);
    }

    @Test
    void KNOWN_권장량에서_현재_섭취량을_뺀다() {
        DoseResult r = calc("TEST_A", "M", 30, Set.of(), List.of(intake("TEST_A", "30", "mg")));

        assertThat(r.amount()).isEqualByComparingTo("70");
        assertThat(r.unit()).isEqualTo("mg");
        assertThat(r.currentIntake()).isEqualByComparingTo("30");
        assertThat(r.intakeStatus()).isEqualTo(DoseCalculator.KNOWN);
        assertThat(r.doseNote()).isNull();
    }

    @Test
    void 같은_성분_여러_제품은_합산() {
        DoseResult r = calc("TEST_A", "M", 30, Set.of(),
                List.of(intake("TEST_A", "30", "mg"), intake("TEST_A", "20", "mg"), intake("TEST_B", "5", "mcg")));

        assertThat(r.amount()).isEqualByComparingTo("50");
    }

    @Test
    void UNKNOWN_현재_섭취량이_없으면_권장량_전체() {
        DoseResult r = calc("TEST_A", "F", 30, Set.of(), List.of());

        assertThat(r.amount()).isEqualByComparingTo("80");
        assertThat(r.intakeStatus()).isEqualTo(DoseCalculator.UNKNOWN);
        assertThat(r.doseNote()).isEqualTo(DoseCalculator.NOTE_INTAKE_UNKNOWN);
    }

    @Test
    void 현재_섭취량이_권장량_이상이면_0이고_목록에는_남긴다() {
        DoseResult r = calc("TEST_A", "M", 30, Set.of(), List.of(intake("TEST_A", "150", "mg")));

        assertThat(r.amount()).isEqualByComparingTo("0");
        assertThat(r.doseNote()).isEqualTo(DoseCalculator.NOTE_NOT_NEEDED);
    }

    @Test
    void 상한_초과면_상한까지로_줄인다() {
        // TEST ONLY 상한 값으로 상한 안쪽 / 초과 / 이미 초과 세 경우를 본다
        List<Standard> lowUl = List.of(std("TEST_D", "ALL", 19, null, null, "100", "120", "mg"));
        DoseResult r = DoseCalculator.calculate("TEST_D", "M", 30, Set.of(),
                List.of(intake("TEST_D", "50", "mg")), lowUl);
        // 권장 100 - 현재 50 = 50, 현재 50 + 50 = 100 ≤ 120 → 그대로
        assertThat(r.amount()).isEqualByComparingTo("50");

        List<Standard> tinyUl = List.of(std("TEST_D", "ALL", 19, null, null, "100", "60", "mg"));
        DoseResult over = DoseCalculator.calculate("TEST_D", "M", 30, Set.of(),
                List.of(intake("TEST_D", "20", "mg")), tinyUl);
        // 권장 100 - 현재 20 = 80, 현재 20 + 80 = 100 > 상한 60 → 60 - 20 = 40
        assertThat(over.amount()).isEqualByComparingTo("40");
        assertThat(over.doseNote()).contains(DoseCalculator.NOTE_UPPER_LIMIT);

        DoseResult alreadyOver = DoseCalculator.calculate("TEST_D", "M", 30, Set.of(),
                List.of(intake("TEST_D", "70", "mg")), tinyUl);
        assertThat(alreadyOver.amount()).isEqualByComparingTo("0");
    }

    @Test
    void 흡연자는_SMOKING_CURRENT_조건_행을_우선() {
        DoseResult r = calc("TEST_A", "M", 30, Set.of("SMOKING_CURRENT"), List.of());

        assertThat(r.amount()).isEqualByComparingTo("140");
        assertThat(r.standard().conditionCode()).isEqualTo("SMOKING_CURRENT");
    }

    @Test
    void 성별_행이_ALL_행보다_우선하고_나이_범위가_좁은_행이_우선() {
        assertThat(calc("TEST_A", "M", 30, Set.of(), List.of()).amount()).isEqualByComparingTo("100");
        // 성별 F 행이 있으므로 ALL(19-49)보다 F가 우선
        assertThat(calc("TEST_A", "F", 30, Set.of(), List.of()).amount()).isEqualByComparingTo("80");
        // TEST_B: 55세는 50-64 행(더 좁음)
        assertThat(calc("TEST_B", "M", 55, Set.of(), List.of()).amount()).isEqualByComparingTo("60");
        assertThat(calc("TEST_B", "M", 30, Set.of(), List.of()).amount()).isEqualByComparingTo("50");
    }

    @Test
    void 기준이_없거나_권장량이_비어_있으면_용량_없이_기준값_미등록() {
        DoseResult none = calc("TEST_Z", "M", 30, Set.of(), List.of());
        DoseResult empty = calc("TEST_C", "M", 30, Set.of(), List.of());
        DoseResult young = calc("TEST_A", "M", 15, Set.of(), List.of());

        assertThat(none.amount()).isNull();
        assertThat(none.doseNote()).isEqualTo(DoseCalculator.NOTE_NO_STANDARD);
        assertThat(empty.amount()).isNull();
        assertThat(young.amount()).isNull();
    }

    @Test
    void 단위가_다르면_변환하지_않고_UNKNOWN() {
        DoseResult r = calc("TEST_B", "F", 30, Set.of(), List.of(intake("TEST_B", "1", "mg")));

        assertThat(r.intakeStatus()).isEqualTo(DoseCalculator.UNKNOWN);
        assertThat(r.amount()).isEqualByComparingTo("50");
        assertThat(r.doseNote()).isEqualTo(DoseCalculator.NOTE_UNIT_MISMATCH);
    }

    @Test
    void 상한_초과_판정() {
        assertThat(DoseCalculator.exceedsUpperLimit("TEST_A", "M", 30, Set.of(),
                List.of(intake("TEST_A", "200", "mg")), TEST_ONLY, new BigDecimal("150"), "mg")).isTrue();
        assertThat(DoseCalculator.exceedsUpperLimit("TEST_A", "M", 30, Set.of(),
                List.of(), TEST_ONLY, new BigDecimal("150"), "mg")).isFalse();
        assertThat(DoseCalculator.exceedsUpperLimit("TEST_B", "M", 30, Set.of(),
                List.of(), TEST_ONLY, new BigDecimal("150"), "mcg")).isNull();
    }
}
