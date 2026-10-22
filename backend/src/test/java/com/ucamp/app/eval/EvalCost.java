package com.ucamp.app.eval;

import java.util.Map;

/**
 * 요청당 비용 계산. 단가는 docs/perf/pricing.md (OpenAI 공식 가격표, 2026-10-22 확인)와 같아야 한다.
 * 캐시 쓰기는 입력 단가의 1.25배(30분 보관, GPT-5.6 이후 모델), 캐시 읽기는 가격표의 cached input 단가.
 * Fake 모델(이름 끝이 -fake)은 실제 비용이 없으므로 null.
 */
public final class EvalCost {
    /** USD / 100만 토큰: 입력, 캐시 쓰기(30분), 캐시 읽기, 출력 */
    public record Price(double input, double cacheWrite, double cacheRead, double output) {
    }

    public static final Map<String, Price> PRICES = Map.of(
            "gpt-6-astra", new Price(10.00, 12.50, 1.00, 50.00),
            "gpt-6.1-sol", new Price(2.00, 2.50, 0.10, 10.00),
            "gpt-6-luna", new Price(0.10, 0.125, 0.01, 0.50));

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
