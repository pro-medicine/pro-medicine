package com.ucamp.app.domain.recommendation.service;

/** V7.1: 회원 추천이 DONE으로 저장됨 (커밋 뒤 제품 순위 스냅샷용) */
public record RecommendationCompletedEvent(Long recommendationId) {
}
