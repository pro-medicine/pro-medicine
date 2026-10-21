package com.ucamp.app.eval;

import java.util.Map;

/**
 * 요청당 비용 계산. 단가는 docs/perf/pricing.md (Anthropic 공식 가격표, 2026-10-21 확인)와 같아야 한다.
 * Fake 모델(이름 끝이 -fake)은 실제 비용이 없으므로 null.
 */
public final class EvalCost {
    /** USD / 100만 토큰: 입력, 캐시 쓰기(5분), 캐시 읽기, 출력 */
    public record Price(double input, double cacheWrite, double cacheRead, double output) {
    }

    public static final Map<String, Price> PRICES = Map.of(
            "claude-opus-5-5", new Price(4.00, 5.00, 0.20, 20.00),
            "claude-sonnet-5-5", new Price(2.00, 2.50, 0.10, 10.00),
            "claude-haiku-5-5", new Price(0.10, 0.125, 0.01, 0.50));

    private EvalCost() {
    }

    public static Double cost(String model, long input, long cacheWrite, long cacheRead, long output) {
        if (model == null || model.endsWith("-fake")) {
            return null;
        }
        Price p = PRICES.get(model);
        if (p == null) {
            return null;
        }
        return (input * p.input() + cacheWrite * p.cacheWrite() + cacheRead * p.cacheRead() + output * p.output())
                / 1_000_000.0;
    }

    public static Price price(String model) {
        return PRICES.get(model);
    }
}
