package com.ucamp.app.domain.recommendation.pipeline;

/**
 * 추천 파이프라인. 버전별 Before/After를 같은 조건에서 비교하려고 모든 버전을 지우지 않고 공존시킨다.
 * API는 recommendation.pipeline-version 설정으로, 평가 러너는 인자로 버전을 고른다.
 */
public interface RecommendationPipeline {
    String version();

    RecommendationResult run(RecommendationInput input);
}
