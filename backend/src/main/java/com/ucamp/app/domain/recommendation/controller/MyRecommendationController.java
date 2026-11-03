package com.ucamp.app.domain.recommendation.controller;

import com.ucamp.app.domain.recommendation.dto.LatestRecommendationResponse;
import com.ucamp.app.domain.recommendation.dto.RecentRecommendationResponse;
import com.ucamp.app.domain.recommendation.service.RecentRecommendationService;
import com.ucamp.app.domain.recommendation.service.RecommendationReuseService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/recommendations")
public class MyRecommendationController {
    private final RecommendationReuseService reuseService;
    private final RecentRecommendationService recentService;

    // 알림을 열고 들어온 회원: 저장된 최근 추천을 재사용할 수 있으면 LLM 없이 돌려준다
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<LatestRecommendationResponse>> latest(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(reuseService.latest(userId)));
    }

    // V7 마이페이지 "최근 추천 제품 보기": V1·V2·V4 중 가장 최근 추천 1건 + 성분별 제품. 저장된 결과만 읽는다 (LLM 없음)
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<RecentRecommendationResponse>> recent(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(recentService.recent(userId)));
    }
}
