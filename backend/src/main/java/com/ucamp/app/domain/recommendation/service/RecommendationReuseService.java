package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.domain.recommendation.dto.LatestRecommendationResponse;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 알림을 열고 들어온 회원의 최근 추천 조회 (STEP 09 2-2). LLM을 부르지 않는다 (STEP 08 결과 캐시와 별개).
 * 재사용 조건: 가장 최근 DONE 추천 이후 health_profiles · user_conditions · user_medications 변경이 없다.
 * 조건을 못 맞추면 새 설문을 하도록 안내한다 (needsNewSurvey). 새로 만들어야 하는 요청만 큐로 간다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationReuseService {
    public static final String NO_RECOMMENDATION = "NO_RECOMMENDATION";
    public static final String PROFILE_CHANGED = "PROFILE_CHANGED";

    private final RecommendationStore store;
    private final HealthProfileService healthProfileService;

    public LatestRecommendationResponse latest(Long userId) {
        Optional<RecommendationResponse> latest = store.latestDoneForUser(userId);
        if (latest.isEmpty()) {
            return new LatestRecommendationResponse(false, true, NO_RECOMMENDATION, null);
        }
        LocalDateTime changed = healthProfileService.lastChangedAt(userId);
        if (changed != null && !changed.isBefore(latest.get().createdAt())) {
            return new LatestRecommendationResponse(false, true, PROFILE_CHANGED, latest.get());
        }
        return new LatestRecommendationResponse(true, false, null, latest.get());
    }
}
