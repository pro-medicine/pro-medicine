package com.ucamp.app.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 측정값(ms) 모음과 백분위 계산. 백분위는 nearest-rank 방식
public class PerfStats {
    private final List<Double> values = new ArrayList<>();

    public void add(double ms) {
        values.add(ms);
    }

    public int count() {
        return values.size();
    }

    public double percentile(double p) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    public double max() {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    public double mean() {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", count());
        m.put("p50_ms", round(percentile(50)));
        m.put("p95_ms", round(percentile(95)));
        m.put("p99_ms", round(percentile(99)));
        m.put("max_ms", round(max()));
        m.put("mean_ms", round(mean()));
        return m;
    }

    public static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
