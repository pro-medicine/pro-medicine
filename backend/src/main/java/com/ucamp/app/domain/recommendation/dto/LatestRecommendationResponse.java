package com.ucamp.app.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GET /api/v1/users/me/recommendations/latest 응답.
 *
 * @param reusable       true면 저장된 추천을 그대로 보여주면 된다 (LLM 호출 없음)
 * @param needsNewSurvey true면 새 설문 → 새 추천이 필요하다
 * @param reason         NO_RECOMMENDATION | PROFILE_CHANGED (needsNewSurvey일 때)
 * @param recommendation 가장 최근 완료 추천 (PROFILE_CHANGED여도 참고용으로 내려준다)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LatestRecommendationResponse(boolean reusable, boolean needsNewSurvey, String reason,
                                           RecommendationResponse recommendation) {
}
