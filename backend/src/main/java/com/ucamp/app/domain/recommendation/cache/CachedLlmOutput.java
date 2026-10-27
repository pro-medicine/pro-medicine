package com.ucamp.app.domain.recommendation.cache;

import java.util.List;

/**
 * 결과 캐시에 저장하는 값: LLM 출력(성분·이유·순서·요약)만. 개인 식별 정보는 없다.
 * 용량 계산과 규칙 검증은 캐시 적중이어도 매번 다시 한다 (현재 섭취량이 다를 수 있음).
 */
public record CachedLlmOutput(List<Item> items, String summary, String model) {
    public record Item(String ingredient, String reason, int priority) {
    }
}
