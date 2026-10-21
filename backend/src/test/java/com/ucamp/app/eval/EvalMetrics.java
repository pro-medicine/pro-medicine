package com.ucamp.app.eval;

import com.ucamp.app.eval.EvalCaseFile.Dose;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * reference/measurement.md 1번 품질 지표 계산 (순수 함수).
 * - recall: 케이스(실행)별 맞춘 mustInclude 비율의 평균. mustInclude가 빈 케이스는 뺀다
 * - precision: 전체 실행 합산 (추천 수 − 허용 밖 수) / 추천 수. 허용 = mustInclude + mayInclude. 중복 성분은 한 번만 센다
 * - violation_rate / raw_violation_rate: 최종(제외 후) / 제외 전 목록에 mustExclude가 하나라도 있는 실행 비율
 * - dose_error_rate: 기대 용량이 있는 성분 중 실제로 추천된 항목 기준. 같은 단위에서 ±0.5 이내면 일치,
 *   단위가 다르거나 용량이 없으면 오류. 추천되지 않은 성분은 recall 쪽에서 다룬다
 * - over_limit_rate: 용량이 있는 항목 중 (현재 + 추천) > 상한인 비율. 상한을 아는 항목만 (STEP 06부터)
 * - reproducibility: 같은 케이스 반복에서 첫 결과와 같은 성분 집합(+용량)이 나온 횟수 / 반복 횟수 (첫 결과 포함)
 * - parse_fail_rate: 재요청까지 끝난 뒤 parseSuccess=false인 실행 비율
 */
public final class EvalMetrics {
    private static final BigDecimal DOSE_TOLERANCE = new BigDecimal("0.5");

    private EvalMetrics() {
    }

    /** 평가용으로 정리한 추천 항목. code가 null이면 별칭 표로 바꾸지 못한 이름 */
    public record Item(String code, String rawName, BigDecimal amount, String unit) {
        public String key() {
            return code != null ? code : "?" + rawName;
        }
    }

    public record Run(EvalCase evalCase, int repeat, boolean parseSuccess, List<Item> finalItems, List<Item> rawItems,
                      List<Boolean> overLimitFlags) {
    }

    public record Result(Double recall, Double precision, Double violationRate, Double rawViolationRate,
                         Double overLimitRate, Integer overLimitN, Double doseErrorRate, int doseN,
                         Double reproducibilitySet, Double reproducibilityDose, Double parseFailRate,
                         int runs, int recallCases, int recommendedTotal) {
    }

    public static Result compute(List<Run> runs, boolean withReproducibility) {
        List<Double> recalls = new ArrayList<>();
        int recommended = 0;
        int outside = 0;
        int violations = 0;
        int rawViolations = 0;
        int parseFails = 0;
        int doseN = 0;
        int doseErrors = 0;
        int limitN = 0;
        int overLimit = 0;

        for (Run run : runs) {
            EvalCase c = run.evalCase();
            Set<String> finalSet = keys(run.finalItems());
            Set<String> rawSet = keys(run.rawItems());
            List<String> mustInclude = c.expected().mustInclude();
            if (!mustInclude.isEmpty()) {
                long hit = mustInclude.stream().filter(finalSet::contains).count();
                recalls.add((double) hit / mustInclude.size());
            }
            Set<String> allowed = new HashSet<>(mustInclude);
            allowed.addAll(c.expected().mayInclude());
            recommended += finalSet.size();
            outside += (int) finalSet.stream().filter(k -> !allowed.contains(k)).count();
            if (c.expected().mustExclude().stream().anyMatch(finalSet::contains)) {
                violations++;
            }
            if (c.expected().mustExclude().stream().anyMatch(rawSet::contains)) {
                rawViolations++;
            }
            if (!run.parseSuccess()) {
                parseFails++;
            }
            Map<String, Item> byCode = firstByKey(run.finalItems());
            for (Dose dose : c.expected().doses()) {
                Item item = byCode.get(dose.ingredient());
                if (item == null) {
                    continue;
                }
                doseN++;
                if (!doseMatches(dose, item)) {
                    doseErrors++;
                }
            }
            if (run.overLimitFlags() != null) {
                for (Boolean flag : run.overLimitFlags()) {
                    if (flag == null) {
                        continue;
                    }
                    limitN++;
                    if (flag) {
                        overLimit++;
                    }
                }
            }
        }

        int n = runs.size();
        Double reproSet = null;
        Double reproDose = null;
        if (withReproducibility) {
            double[] r = reproducibility(runs);
            reproSet = r[0];
            reproDose = r[1];
        }
        boolean limitKnown = runs.stream().anyMatch(r -> r.overLimitFlags() != null);
        Double overLimitRate = null;
        if (limitKnown) {
            overLimitRate = limitN > 0 ? (double) overLimit / limitN : 0.0;
        }
        return new Result(
                recalls.isEmpty() ? null : recalls.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                recommended == 0 ? null : (double) (recommended - outside) / recommended,
                n == 0 ? null : (double) violations / n,
                n == 0 ? null : (double) rawViolations / n,
                overLimitRate,
                limitKnown ? limitN : null,
                doseN == 0 ? null : (double) doseErrors / doseN,
                doseN,
                reproSet, reproDose,
                n == 0 ? null : (double) parseFails / n,
                n, recalls.size(), recommended);
    }

    static boolean doseMatches(Dose expected, Item item) {
        if (item.amount() == null || item.unit() == null) {
            return false;
        }
        if (!normalizeUnit(expected.unit()).equals(normalizeUnit(item.unit()))) {
            return false;
        }
        return expected.amount().subtract(item.amount()).abs().compareTo(DOSE_TOLERANCE) <= 0;
    }

    static String normalizeUnit(String unit) {
        return unit.toLowerCase().replace("μ", "mc").replace("µ", "mc").replace("ug", "mcg").replaceAll("\\s", "");
    }

    // 케이스별 반복 결과를 첫 결과와 비교. [성분 집합 일치율, 집합+용량 일치율] 케이스 평균
    static double[] reproducibility(List<Run> runs) {
        Map<String, List<Run>> byCase = new LinkedHashMap<>();
        for (Run r : runs) {
            byCase.computeIfAbsent(r.evalCase().caseId(), k -> new ArrayList<>()).add(r);
        }
        double setSum = 0;
        double doseSum = 0;
        for (List<Run> group : byCase.values()) {
            Set<String> firstSet = keys(group.get(0).finalItems());
            Map<String, String> firstDose = doseSignature(group.get(0).finalItems());
            int sameSet = 0;
            int sameDose = 0;
            for (Run r : group) {
                if (keys(r.finalItems()).equals(firstSet)) {
                    sameSet++;
                    if (doseSignature(r.finalItems()).equals(firstDose)) {
                        sameDose++;
                    }
                }
            }
            setSum += (double) sameSet / group.size();
            doseSum += (double) sameDose / group.size();
        }
        int cases = byCase.size();
        return cases == 0 ? new double[]{0, 0} : new double[]{setSum / cases, doseSum / cases};
    }

    private static Map<String, String> doseSignature(List<Item> items) {
        Map<String, String> sig = new LinkedHashMap<>();
        for (Item i : items) {
            sig.putIfAbsent(i.key(), (i.amount() == null ? "-" : i.amount().stripTrailingZeros().toPlainString())
                    + " " + (i.unit() == null ? "-" : normalizeUnit(i.unit())));
        }
        return sig;
    }

    static Set<String> keys(List<Item> items) {
        Set<String> s = new LinkedHashSet<>();
        for (Item i : items) {
            s.add(i.key());
        }
        return s;
    }

    private static Map<String, Item> firstByKey(List<Item> items) {
        Map<String, Item> m = new LinkedHashMap<>();
        for (Item i : items) {
            m.putIfAbsent(i.key(), i);
        }
        return m;
    }

    public static boolean anyUnknown(List<Item> items) {
        return items.stream().map(Item::code).anyMatch(Objects::isNull);
    }
}
