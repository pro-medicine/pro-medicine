package com.ucamp.app.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.eval.EvalCaseFile.Dose;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.eval.EvalCaseFile.Expected;
import com.ucamp.app.eval.EvalMetrics.Item;
import com.ucamp.app.eval.EvalMetrics.Run;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvalMetricsTest {

    private static EvalCase evalCase(String id, List<String> must, List<String> may, List<String> exclude, List<Dose> doses) {
        return new EvalCase(id, List.of(), "EASY", null, new Expected(must, may, exclude, doses), "");
    }

    private static Item item(String code, String amount, String unit) {
        return new Item(code, code, amount == null ? null : new BigDecimal(amount), unit);
    }

    private static Run run(EvalCase c, int rep, List<Item> finalItems) {
        return new Run(c, rep, true, finalItems, finalItems, null);
    }

    private final EvalCase smoker = evalCase("E-001", List.of("VITAMIN_C"), List.of("VITAMIN_B1"),
            List.of("BETA_CAROTENE"), List.of(new Dose("VITAMIN_C", new BigDecimal("125"), "mg")));

    @Test
    void 정답이면_recall_1_violation_0_dose_error_0() {
        var r = EvalMetrics.compute(List.of(run(smoker, 0, List.of(item("VITAMIN_C", "125", "mg")))), false);

        assertThat(r.recall()).isEqualTo(1.0);
        assertThat(r.precision()).isEqualTo(1.0);
        assertThat(r.violationRate()).isEqualTo(0.0);
        assertThat(r.doseErrorRate()).isEqualTo(0.0);
        assertThat(r.doseN()).isEqualTo(1);
        assertThat(r.parseFailRate()).isEqualTo(0.0);
    }

    @Test
    void 틀린_답이면_지표가_나빠진다() {
        var r = EvalMetrics.compute(List.of(run(smoker, 0,
                List.of(item("BETA_CAROTENE", null, null), item("VITAMIN_C", "200", "mg"), item("ZINC", null, null)))), false);

        assertThat(r.recall()).isEqualTo(1.0);
        assertThat(r.precision()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(r.violationRate()).isEqualTo(1.0);
        assertThat(r.rawViolationRate()).isEqualTo(1.0);
        assertThat(r.doseErrorRate()).isEqualTo(1.0);
    }

    @Test
    void 빈_추천은_recall_0이고_precision은_계산하지_않는다() {
        Run failed = new Run(smoker, 0, false, List.of(), List.of(), null);

        var r = EvalMetrics.compute(List.of(failed), false);

        assertThat(r.recall()).isEqualTo(0.0);
        assertThat(r.precision()).isNull();
        assertThat(r.parseFailRate()).isEqualTo(1.0);
        assertThat(r.doseN()).isZero();
    }

    @Test
    void 중복_성분은_한_번만_센다() {
        var r = EvalMetrics.compute(List.of(run(smoker, 0,
                List.of(item("VITAMIN_C", "125", "mg"), item("VITAMIN_C", "125", "mg")))), false);

        assertThat(r.recommendedTotal()).isEqualTo(1);
        assertThat(r.precision()).isEqualTo(1.0);
    }

    @Test
    void mustInclude가_빈_케이스는_recall_평균에서_뺀다() {
        EvalCase none = evalCase("E-023", List.of(), List.of("MAGNESIUM"), List.of(), List.of());

        var r = EvalMetrics.compute(List.of(run(none, 0, List.of(item("MAGNESIUM", null, null))),
                run(smoker, 0, List.of())), false);

        assertThat(r.recallCases()).isEqualTo(1);
        assertThat(r.recall()).isEqualTo(0.0);
    }

    @Test
    void 제외된_성분은_raw_위반에만_잡힌다() {
        Run run = new Run(smoker, 0, true, List.of(item("VITAMIN_C", "125", "mg")),
                List.of(item("VITAMIN_C", "125", "mg"), item("BETA_CAROTENE", null, null)), null);

        var r = EvalMetrics.compute(List.of(run), false);

        assertThat(r.violationRate()).isEqualTo(0.0);
        assertThat(r.rawViolationRate()).isEqualTo(1.0);
    }

    @Test
    void 용량은_같은_단위에서_0_5_이내면_일치_단위가_다르면_오류() {
        Dose d = new Dose("FOLATE", new BigDecimal("400"), "mcg DFE");
        assertThat(EvalMetrics.doseMatches(d, item("FOLATE", "400.4", "mcg DFE"))).isTrue();
        assertThat(EvalMetrics.doseMatches(d, item("FOLATE", "400", "μg DFE"))).isTrue();
        assertThat(EvalMetrics.doseMatches(d, item("FOLATE", "401", "mcg DFE"))).isFalse();
        assertThat(EvalMetrics.doseMatches(d, item("FOLATE", "400", "mcg"))).isFalse();
        assertThat(EvalMetrics.doseMatches(d, item("FOLATE", null, null))).isFalse();
    }

    @Test
    void 재현성_첫_결과와_같은_집합_비율() {
        List<Run> runs = List.of(
                run(smoker, 0, List.of(item("VITAMIN_C", "125", "mg"))),
                run(smoker, 1, List.of(item("VITAMIN_C", "100", "mg"))),
                run(smoker, 2, List.of(item("VITAMIN_C", "125", "mg"), item("ZINC", null, null))),
                run(smoker, 3, List.of(item("VITAMIN_C", "125", "mg"))));

        var r = EvalMetrics.compute(runs, true);

        assertThat(r.reproducibilitySet()).isEqualTo(0.75);
        assertThat(r.reproducibilityDose()).isEqualTo(0.5);
    }

    @Test
    void 별칭_매핑() {
        IngredientAliases aliases = IngredientAliases.load(new ObjectMapper());

        assertThat(aliases.toCode("비타민C")).isEqualTo("VITAMIN_C");
        assertThat(aliases.toCode("Vitamin C")).isEqualTo("VITAMIN_C");
        assertThat(aliases.toCode("아스코르브산")).isEqualTo("VITAMIN_C");
        assertThat(aliases.toCode("비타민 B12 (메틸코발라민)")).isEqualTo("VITAMIN_B12");
        assertThat(aliases.toCode("비타민 B1")).isEqualTo("VITAMIN_B1");
        assertThat(aliases.toCode("β-카로틴")).isEqualTo("BETA_CAROTENE");
        assertThat(aliases.toCode("VITAMIN_D")).isEqualTo("VITAMIN_D");
        assertThat(aliases.toCode("철분")).isEqualTo("IRON");
        assertThat(aliases.toCode("오메가3")).isNull();
    }
}
